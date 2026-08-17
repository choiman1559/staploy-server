package com.staploy.server.commons.utils;

import com.staploy.App;
import io.github.milkdrinkers.javasemver.Version;

public final class SemVersion {

    private SemVersion() {
    }

    public static int compare(App.Version a, App.Version b) {
        return Version.parse(a.getVersionName())
                .compareTo(Version.parse(b.getVersionName()));
    }
}