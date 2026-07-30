/*
 * Copyright © 2013 Keith Packard
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting documentation, and
 * that the name of the copyright holders not be used in advertising or
 * publicity pertaining to distribution of the software without specific,
 * written prior permission.  The copyright holders make no representations
 * about the suitability of this software for any purpose.  It is provided "as
 * is" without express or implied warranty.
 *
 * THE COPYRIGHT HOLDERS DISCLAIM ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO
 * EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY SPECIAL, INDIRECT OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 */

#include "present_priv.h"

/*
 * Called when the wait fence is triggered; just gets the current msc/ust and
 * calls the proper execute again. That will re-check the fence and pend the
 * request again if it's still not actually ready
 */
static void
present_wait_fence_triggered(void *param)
{
    present_vblank_ptr      vblank = param;
    ScreenPtr               screen = vblank->screen;
    present_screen_priv_ptr screen_priv = present_screen_priv(screen);

    screen_priv->re_execute(vblank);
}

Bool
present_execute_wait(present_vblank_ptr vblank, uint64_t crtc_msc)
{
    WindowPtr                   window = vblank->window;
    ScreenPtr                   screen = window->drawable.pScreen;
    present_screen_priv_ptr screen_priv = present_screen_priv(screen);

    /* We may have to requeue for the next MSC if check_flip_window prevented
     * using a flip.
     */
    if (vblank->exec_msc == crtc_msc + 1 &&
        screen_priv->queue_vblank(screen, window, vblank->crtc, vblank->event_id,
                                  vblank->exec_msc) == Success)
        return TRUE;

    if (vblank->wait_fence) {
        if (!present_fence_check_triggered(vblank->wait_fence)) {
            present_fence_set_callback(vblank->wait_fence, present_wait_fence_triggered, vblank);
            return TRUE;
        }
    }
    return FALSE;
}

void
present_execute_copy(present_vblank_ptr vblank, uint64_t crtc_msc)
{
    WindowPtr                   window = vblank->window;
    ScreenPtr                   screen = window->drawable.pScreen;
    present_screen_priv_ptr screen_priv = present_screen_priv(screen);
    /* lorie: screen_x/screen_y is dst's own (0,0) in screen coords; window.xy minus that gives the
     * window's offset within dst, whether dst is root, itself, or an inherited redirected ancestor. */
    PixmapPtr                   gpuCopyDst = screen->GetWindowPixmap(window);
    int16_t                     gpuCopyXOff = (int16_t) (vblank->x_off + window->drawable.x - gpuCopyDst->screen_x);
    int16_t                     gpuCopyYOff = (int16_t) (vblank->y_off + window->drawable.y - gpuCopyDst->screen_y);

    /* If present_flip failed, we may have to requeue for the next MSC */
    if (vblank->exec_msc == crtc_msc + 1 &&
        Success == screen_priv->queue_vblank(screen,
                                             window,
                                             vblank->crtc,
                                             vblank->event_id,
                                             vblank->exec_msc)) {
        vblank->queued = TRUE;
        return;
    }

    /* lorie: a GPU copy was already scheduled; poll for completion instead of blocking. Give up
     * and treat it as done if the renderer connection died (completedSerial would never advance). */
    if (vblank->gpu_copy_pending && !lorieGpuCopyIsDone(vblank->gpu_copy_serial) &&
        lorieConnectionAlive() &&
        Success == screen_priv->queue_vblank(screen, window, vblank->crtc, vblank->event_id, crtc_msc + 1)) {
        vblank->queued = TRUE;
        return;
    }

    if (vblank->gpu_copy_pending) {
        lorieGpuCopyAck(vblank->pixmap, vblank->gpu_copy_dst_buffer);
        vblank->gpu_copy_pending = FALSE;
    } else if (lorieTryScheduleGpuCopy(vblank->pixmap, gpuCopyDst, vblank->update, gpuCopyXOff, gpuCopyYOff,
                                        &vblank->gpu_copy_serial, &vblank->gpu_copy_dst_buffer)) {
        /* lorie: our GPU blit writes the destination pixmap directly, bypassing the normal GC
         * ops that Damage tracking hooks into, so report it manually - same drawable and region
         * present_scmd.c's flip success path already uses, so this also reaches Composite's
         * per-window damage for redirected windows, not just lorie's own root damage. */
        RegionPtr damage = vblank->update ? vblank->update : &window->clipList;
        if (vblank->update)
            RegionIntersect(damage, damage, &window->clipList);
        DamageDamageRegion(&window->drawable, damage);

        /* lorie: copy offloaded to the renderer's GPU context. The region was already
         * consumed (copied into the shared command queue), so free it like
         * present_copy_region would have. */
        if (vblank->update) {
            RegionDestroy(vblank->update);
            vblank->update = NULL;
        }
        if (Success == screen_priv->queue_vblank(screen, window, vblank->crtc, vblank->event_id, crtc_msc + 1)) {
            vblank->gpu_copy_pending = TRUE;
            vblank->queued = TRUE;
            return;
        }
        /* Failed to requeue for polling (e.g. OOM) - release our extra ref and treat the
         * presumably still in-flight GPU copy as done; same best-effort fallback as above. */
        lorieGpuCopyAck(vblank->pixmap, vblank->gpu_copy_dst_buffer);
    } else {
        present_copy_region(&window->drawable, vblank->pixmap, vblank->update, vblank->x_off, vblank->y_off);

        /* present_copy_region sticks the region into a scratch GC,
         * which is then freed, freeing the region
         */
        vblank->update = NULL;
    }

    screen_priv->flush(window);

    present_pixmap_idle(vblank->pixmap, vblank->window, vblank->serial, vblank->idle_fence);
}

void
present_execute_post(present_vblank_ptr vblank, uint64_t ust, uint64_t crtc_msc)
{
    uint8_t mode;

    /* Compute correct CompleteMode
     */
    if (vblank->kind == PresentCompleteKindPixmap) {
        if (vblank->pixmap && vblank->window) {
            if (vblank->has_suboptimal && vblank->reason == PRESENT_FLIP_REASON_BUFFER_FORMAT)
                mode = PresentCompleteModeSuboptimalCopy;
            else
                mode = PresentCompleteModeCopy;
        } else {
            mode = PresentCompleteModeSkip;
        }
    }
    else
        mode = PresentCompleteModeCopy;

    present_vblank_notify(vblank, vblank->kind, mode, ust, crtc_msc);
    present_vblank_destroy(vblank);
}
