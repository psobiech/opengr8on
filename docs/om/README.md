# Run in Wayland using xwayland

Since OM uses legacy Java8, to run under Wayland, we need to force X11/XWayland GDK support.

Export the following environment variable:
> GDK_BACKEND=x11

If you're affected by the issue, you will see the following message:

```
2023-12-06 11:53:45.860 [ERROR][main] STDERR:63 Dec 06, 2023 11:53:45 AM com.sun.glass.ui.gtk.GtkApplication <clinit>
WARNING: SWT-GTK uses unsupported major GTK version 0. GTK3 will be used as default.

(Object Manager:85814): Gdk-CRITICAL **: 11:53:45.863: gdk_x11_display_set_window_scale: assertion 'GDK_IS_X11_DISPLAY (display)' failed
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x00007f53e00804ef, pid=85814, tid=0x00007f5423bff6c0
#
# JRE version: Java(TM) SE Runtime Environment (8.0_202-b08) (build 1.8.0_202-b08)
# Java VM: Java HotSpot(TM) 64-Bit Server VM (25.202-b08 mixed mode linux-amd64 compressed oops)
# Problematic frame:
# C  [libX11.so.6+0x2d4ef]  XInternAtom+0x3f
#
# Core dump written. (...)
```