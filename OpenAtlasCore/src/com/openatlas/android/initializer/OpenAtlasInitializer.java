package com.openatlas.android.initializer;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;

import com.openatlas.android.task.Coordinator;
import com.openatlas.android.task.Coordinator.TaggedRunnable;
import com.openatlas.boot.Globals;
import com.openatlas.framework.Atlas;
import com.openatlas.framework.PlatformConfigure;
import com.openatlas.log.Logger;
import com.openatlas.log.LoggerFactory;
import com.openatlas.util.ApkUtils;

import java.util.Properties;

public class OpenAtlasInitializer {
	Logger log=LoggerFactory.getInstance("OpenAtlasInitializer");
    private static long initStartTime = 0;
    private static boolean inTargetApp;
    private Application mApplication;
    private String mPackageName;
    private BundleDebug mDebug;
    private boolean tryInstall;

    private Properties mProperties = new Properties();
    private boolean isUpdate = false;


    public OpenAtlasInitializer(Application application, String packagename, boolean isUpdate) {
        this.mApplication = application;
        this.mPackageName = packagename;

        this.isUpdate = isUpdate;
        if (application.getPackageName().equals(packagename)) {
            inTargetApp = true;
        }
    }

    public void init() {
    
        initStartTime = System.currentTimeMillis();
        setupMonitor();
        setupLog();
        try {
            Atlas.getInstance().init(this.mApplication);
            log.debug("OpenAtlas framework inited end " + this.mPackageName + " " + (System.currentTimeMillis() - initStartTime) + " ms");
        } catch (Throwable e) {
            Log.e("OpenAtlasInitializer", "Could not init atlas framework !!!", e);
            throw new RuntimeException("atlas initialization fail" + e.getMessage());
        }
    }

    public void startUp() {

        this.mProperties.put(PlatformConfigure.BOOT_ACTIVITY, PlatformConfigure.BOOT_ACTIVITY);
        this.mProperties.put(PlatformConfigure.COM_OPENATLAS_DEBUG_BUNDLES, "true");
        this.mProperties.put(PlatformConfigure.ATLAS_APP_DIRECTORY, this.mApplication.getFilesDir().getParent());

        try {

            Globals.init(this.mApplication, Atlas.getInstance().getDelegateClassLoader());
            this.mDebug = new BundleDebug();
            if (this.mApplication.getPackageName().equals(this.mPackageName)) {
                if (!( verifyRuntime() || !ApkUtils.isRootSystem())) {
                    this.mProperties.put(PlatformConfigure.OPENATLAS_PUBLIC_KEY, SecurityBundleListner.PUBLIC_KEY);
                    Atlas.getInstance().addBundleListener(new SecurityBundleListner());
                }
                if (this.isUpdate || this.mDebug.isDebugable()) {
                    this.mProperties.put("osgi.init", "true");
                }
            }
           BundlesInstaller mBundlesInstaller = BundlesInstaller.getInstance();
            OptDexProcess mOptDexProcess = OptDexProcess.getInstance();
            if (this.mApplication.getPackageName().equals(this.mPackageName) && (this.isUpdate || this.mDebug.isDebugable())) {
            	mBundlesInstaller.init(this.mApplication,  this.mDebug, inTargetApp);
                mOptDexProcess.init(this.mApplication);
            }
            log.debug( "OpenAtlas framework prepare starting in process " + this.mPackageName + " " + (System.currentTimeMillis() - initStartTime) + " ms");
            Atlas.getInstance().setClassNotFoundInterceptorCallback(new ClassNotFoundInterceptor());
            if (InstallSolutionConfig.install_when_findclass ) {
            	InstallSolutionConfig.install_when_oncreate = true;
                this.tryInstall = true;
            }

            try {
                Atlas.getInstance().startup(this.mProperties);
                installBundles(mBundlesInstaller, mOptDexProcess);
                log.debug("OpenAtlas framework end startUp in process " + this.mPackageName + " " + (System.currentTimeMillis() - initStartTime) + " ms");
            } catch (Throwable e) {
                Log.e("OpenAtlasInitializer", "Could not start up atlas framework !!!", e);
                throw new RuntimeException(e);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Could not set Globals !!!", e);
        }
    }

    private void installBundles(final BundlesInstaller mBundlesInstaller, final OptDexProcess mOptDexProcess) {

        if (this.mDebug.isDebugable()) {
        	InstallSolutionConfig.install_when_oncreate = true;
        }
        if (this.mApplication.getPackageName().equals(this.mPackageName)) {
            if (InstallSolutionConfig.install_when_oncreate) {

            }
            AutoStartBundlesLaunch autoStartBundlesLaunch = new AutoStartBundlesLaunch();
            if (this.isUpdate || this.mDebug.isDebugable()) {
                if (InstallSolutionConfig.install_when_oncreate) {
                    Coordinator.postTask(new  TaggedRunnable("AtlasStartup") {
						@Override
						public void run() {
							mBundlesInstaller.process(true, false);
							mOptDexProcess.processPackages(true, false);
							
						}
					});

                    return;
                }
                Utils.UpdatePackageVersion(this.mApplication);
                Utils.saveAtlasInfoBySharedPreferences(this.mApplication);

                autoStartBundlesLaunch.startAutoBundles();
            } else if (!this.isUpdate) {
                if (this.tryInstall) {
                    Coordinator.postTask(new TaggedRunnable("AtlasStartup") {
						@Override
						public void run() {
							mBundlesInstaller.process(true, false);
							mOptDexProcess.processPackages(true, false);
						}
					});
                    return;
                }
                autoStartBundlesLaunch.startAutoBundles();
            }
        }
    }



    @SuppressLint({"DefaultLocale"})
    private boolean verifyRuntime() {
    
        if ((Build.BRAND == null || !Build.BRAND.toLowerCase().contains("xiaomi") || Build.HARDWARE == null || !Build.HARDWARE.toLowerCase().contains("mt65")) && VERSION.SDK_INT >= 14) {
            return false;
        }
        return true;
    }

    private void setupMonitor() {
    }

    private void setupLog() {

        Atlas.getInstance().setLogger(new ExternalLog());
    }
}
