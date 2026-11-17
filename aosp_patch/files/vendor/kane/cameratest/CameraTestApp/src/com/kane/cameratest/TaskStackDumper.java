package com.kane.cameratest;

import android.app.ActivityTaskManager;
import android.app.WindowConfiguration;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Button 4: which tasks/activities are in the stack right now. Two parts:
 * root tasks from ActivityTaskManager (top first), then every activity record per task from
 * {@code dumpsys activity activities}. Call off the main thread (the dump can take a while).
 */
final class TaskStackDumper {
    private static final String TAG = "CameraTest/Stack";

    static String dump(int displayId) {
        final StringBuilder sb = new StringBuilder();
        appendRootTasks(sb, displayId);
        sb.append('\n');
        appendActivityRecords(sb);
        final String text = sb.toString();
        for (String line : text.split("\n")) {
            Log.i(TAG, line);
        }
        return text;
    }

    private static void appendRootTasks(StringBuilder sb, int displayId) {
        final List<ActivityTaskManager.RootTaskInfo> rootTasks;
        try {
            rootTasks = ActivityTaskManager.getService().getAllRootTaskInfosOnDisplay(displayId);
        } catch (RemoteException | SecurityException e) {
            sb.append("getAllRootTaskInfosOnDisplay failed: ").append(e).append('\n');
            return;
        }
        sb.append("== Root tasks on display ").append(displayId).append(" (top first): ")
                .append(rootTasks.size()).append('\n');
        for (int i = 0; i < rootTasks.size(); i++) {
            final ActivityTaskManager.RootTaskInfo rt = rootTasks.get(i);
            final WindowConfiguration wc = rt.configuration.windowConfiguration;
            sb.append(String.format("#%d rootTask=%d %s/%s visible=%b activities=%d\n",
                    i, rt.taskId,
                    WindowConfiguration.windowingModeToString(wc.getWindowingMode()),
                    WindowConfiguration.activityTypeToString(wc.getActivityType()),
                    rt.visible, rt.numActivities));
            sb.append("     top=").append(shortName(rt.topActivity)).append('\n');
            if (rt.childTaskIds != null) {
                for (int j = 0; j < rt.childTaskIds.length; j++) {
                    sb.append(String.format("     task %d u%d %s\n", rt.childTaskIds[j],
                            rt.childTaskUserIds[j], rt.childTaskNames[j]));
                }
            }
        }
    }

    /** Task and "Hist" (activity record) lines of dumpsys activity activities, in z-order. */
    private static void appendActivityRecords(StringBuilder sb) {
        sb.append("== Activities per task (dumpsys activity activities)\n");
        final IBinder am = ServiceManager.getService("activity");
        if (am == null) {
            sb.append("activity service not found\n");
            return;
        }
        ParcelFileDescriptor[] pipe = null;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            am.dumpAsync(pipe[1].getFileDescriptor(), new String[] {"activities"});
            pipe[1].close();
            pipe[1] = null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(pipe[0].getFileDescriptor())))) {
                String line;
                while ((line = r.readLine()) != null) {
                    final String t = line.trim();
                    if (t.startsWith("* Task{") || t.startsWith("* Hist")
                            || t.startsWith("TaskDisplayArea ")) {
                        sb.append(line).append('\n');
                    }
                }
            }
        } catch (IOException | RemoteException e) {
            sb.append("dump failed: ").append(e).append('\n');
        } finally {
            closeQuietly(pipe);
        }
    }

    private static String shortName(android.content.ComponentName cn) {
        return cn == null ? "null" : cn.flattenToShortString();
    }

    private static void closeQuietly(ParcelFileDescriptor[] pipe) {
        if (pipe == null) {
            return;
        }
        for (ParcelFileDescriptor pfd : pipe) {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private TaskStackDumper() {}
}
