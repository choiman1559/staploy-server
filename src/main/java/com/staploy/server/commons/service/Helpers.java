package com.staploy.server.commons.service;

import com.staploy.server.admin.pkg.AppPersists;
import com.staploy.server.commons.blobs.FileRouteManager;
import com.staploy.server.commons.utils.PersistsHelper;
import com.staploy.server.worker.WorkerManager;

import java.util.ArrayList;

public class Helpers {
    private static Helpers helpers;
    private final ArrayList<InitHelperModule> initHelperModules;

    private final PersistsHelper persistsHelper;
    private final WorkerManager workerManager;
    private final FileRouteManager fileRouteManager;
    private final AppPersists appPersists;

    private Helpers() {
        initHelperModules = new ArrayList<>();
        persistsHelper = new PersistsHelper();
        workerManager = new WorkerManager();
        fileRouteManager = new FileRouteManager();
        appPersists = new AppPersists();

        initHelperModules.add(persistsHelper);
        initHelperModules.add(workerManager);
        initHelperModules.add(fileRouteManager);
        initHelperModules.add(appPersists);
    }

    public static Helpers getInstance() {
        if(helpers == null) helpers = new Helpers();
        return helpers;
    }

    public static PersistsHelper getPersistsHelper() {
        return getInstance().persistsHelper;
    }

    public static WorkerManager getWorkerManager() {
        return getInstance().workerManager;
    }

    public static FileRouteManager getFileRouteManager() {
        return getInstance().fileRouteManager;
    }

    public static AppPersists getAppPersists() {
        return getInstance().appPersists;
    }

    public static void invokeOnLoad() {
        for(InitHelperModule initHelperModule : getInstance().initHelperModules) {
            initHelperModule.onServiceAttache();
        }
    }

    public static void invokeOnDead() {
        for(InitHelperModule initHelperModule : getInstance().initHelperModules) {
            initHelperModule.onServiceDetache();
        }
    }
}
