package top.eiyooooo.easycontrol.app.helper;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;

import top.eiyooooo.easycontrol.app.R;

/**
 * 内置高德地图车机版的安装器。
 * <p>
 * APK 由构建脚本下载到 assets/amap_auto.apk，安装时通过 PackageInstaller 会话
 * 流式写入系统安装器，不落地到公共存储，也不需要 FileProvider。
 */
public class AmapInstaller {
  public static final String PACKAGE_NAME = "com.autonavi.amapauto";
  private static final String ASSET_NAME = "amap_auto.apk";
  private static final int BUFFER_SIZE = 64 * 1024;

  // 构建时是否成功内置了安装包
  public static boolean hasBundledApk(Context context) {
    try (InputStream in = context.getAssets().open(ASSET_NAME)) {
      return in.read() != -1;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isInstalled(Context context) {
    try {
      context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static void launch(Context context) {
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
    if (intent == null) {
      Toast.makeText(context, context.getString(R.string.amap_launch_failed), Toast.LENGTH_LONG).show();
      return;
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
    } catch (Exception e) {
      Toast.makeText(context, context.getString(R.string.amap_launch_failed), Toast.LENGTH_LONG).show();
    }
  }

  // 已安装则直接启动，否则从内置包安装
  public static void installOrOpen(Activity activity) {
    if (isInstalled(activity)) {
      launch(activity);
      return;
    }
    if (!hasBundledApk(activity)) {
      Toast.makeText(activity, activity.getString(R.string.amap_not_bundled), Toast.LENGTH_LONG).show();
      return;
    }
    Toast.makeText(activity, activity.getString(R.string.amap_installing), Toast.LENGTH_LONG).show();
    android.app.ProgressDialog dialog = new android.app.ProgressDialog(activity);
    dialog.setMessage(activity.getString(R.string.amap_installing));
    dialog.setCancelable(false);
    try {
      dialog.show();
    } catch (Exception ignored) {
    }
    new Thread(() -> {
      PackageInstaller.Session session = null;
      try {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        session = installer.openSession(sessionId);

        try (InputStream in = activity.getAssets().open(ASSET_NAME);
             OutputStream out = session.openWrite("amap_auto", 0, -1)) {
          byte[] buffer = new byte[BUFFER_SIZE];
          int len;
          while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
          session.fsync(out);
        }

        Intent result = new Intent(activity, InstallResultReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, sessionId, result, flags);
        session.commit(pendingIntent.getIntentSender());
      } catch (Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.amap_install_failed, message), Toast.LENGTH_LONG).show());
      } finally {
        activity.runOnUiThread(() -> {
          try {
            dialog.dismiss();
          } catch (Exception ignored) {
          }
        });
        if (session != null) {
          try {
            session.close();
          } catch (Exception ignored) {
          }
        }
      }
    }).start();
  }

  public static class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
      if (status == PackageInstaller.STATUS_SUCCESS) {
        Toast.makeText(context, context.getString(R.string.amap_install_success), Toast.LENGTH_LONG).show();
        launch(context);
      } else {
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Toast.makeText(context, context.getString(R.string.amap_install_failed, message == null ? String.valueOf(status) : message), Toast.LENGTH_LONG).show();
      }
    }
  }
}
