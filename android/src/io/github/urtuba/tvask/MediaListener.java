package io.github.urtuba.tvask;

import android.service.notification.NotificationListenerService;

// MediaListener does nothing itself. An enabled notification listener may
// read the TV's media sessions, so TvContext can say what is playing.
// Enable it with adb, see AGENTS.md.
public class MediaListener extends NotificationListenerService {
}
