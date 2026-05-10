package com.staploy.server.admin.pkg;

import com.google.protobuf.util.JsonFormat;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.server.commons.service.Service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AppPackage {
    private static final String METADATA_FILE = ".metadata";
    private static final String APP_PATH = "apps";
    private static final String SHARE_ARCH_PATH = "share";

    private App.AppInfo appInfo;
    private App.Version baseVersionInfo;

    private File baseOutputDir;
    private File originalFile;
    private final HashMap<Protocol.CpuArch, ArchPackageBundle> outputArchives;

    public static class ArchPackageBundle {
        private final Protocol.CpuArch cpuArch;
        private App.Version byArchVersionInfo;
        private File outputArchive;

        public ArchPackageBundle(Protocol.CpuArch cpuArch, App.Version baseVersion) {
            this.cpuArch = cpuArch;
            this.byArchVersionInfo = baseVersion;
        }

        public void setByArchVersionInfo(App.Version byArchVersionInfo) {
            this.byArchVersionInfo = byArchVersionInfo;
        }

        public App.Version getByArchVersionInfo() {
            return byArchVersionInfo;
        }

        public File getOutput(String appName, File baseDir) {
            if(outputArchive == null) {
                outputArchive = new File(baseDir, String.format("%s-%s-%s.tar", appName, byArchVersionInfo.getVersionName(), cpuArch.toString()));
            }
            return outputArchive;
        }
    }

    private AppPackage() {
        outputArchives = new HashMap<>();
    }

    public static AppPackage createParser(File parseTarget) {
        AppPackage appPackage = new AppPackage();
        appPackage.originalFile = parseTarget;
        return appPackage;
    }

    public boolean isParsed() {
        return appInfo != null;
    }

    @Nullable
    public App.AppInfo getAppInfo() {
        return appInfo;
    }

    @Nullable
    public App.Version getBaseVersionInfo() {
        return baseVersionInfo;
    }

    public Set<Protocol.CpuArch> getAvailableArch() {
        return outputArchives.keySet();
    }

    public void parse() throws IllegalFormatException, IOException {
        appInfo = null;
        baseVersionInfo = null;
        outputArchives.clear();

        if(originalFile == null) throw new IllegalStateException("Parse target not specified");
        checkMeta : try (TarFile tarFile = new TarFile(originalFile)) {
            final List<TarArchiveEntry> tarArchiveEntries = tarFile.getEntries();
            TarArchiveEntry entry = tarArchiveEntries.stream()
                    .filter(e -> e.isFile() && e.getName().equals(METADATA_FILE))
                    .findFirst()
                    .orElse(null);

            if (entry != null) {
                try (InputStream is = tarFile.getInputStream(entry)) {
                    byte[] content = IOUtils.toByteArray(is);
                    App.InstalledAppInfo.Builder installedInfo = App.InstalledAppInfo.newBuilder();
                    JsonFormat.parser().merge(new String(content), installedInfo);

                    appInfo = installedInfo.getApp();
                    baseVersionInfo = installedInfo.getCurrentVersion();
                }
            } else break checkMeta;

            List<TarArchiveEntry> archEntries = tarArchiveEntries.stream()
                    .filter(e -> e.isDirectory() && e.getName().split("/").length == 1)
                    .toList();

            for(TarArchiveEntry archiveEntry : archEntries) {
                Protocol.CpuArch cpuArch = getArchByTag(archiveEntry.getName());
                outputArchives.put(cpuArch, new ArchPackageBundle(cpuArch, baseVersionInfo));
            }

            List<TarArchiveEntry> byArchEntries = tarArchiveEntries.stream()
                    .filter(e -> e.isFile() && e.getName().endsWith(METADATA_FILE) && e.getName().split("/").length == 2)
                    .toList();

            for(TarArchiveEntry byArchMetadata : byArchEntries) {
                Protocol.CpuArch cpuArch = getArchByTag(byArchMetadata.getName().split("/")[0]);
                App.Version.Builder version = App.Version.newBuilder(outputArchives.get(cpuArch).getByArchVersionInfo());

                try (InputStream is = tarFile.getInputStream(byArchMetadata)) {
                    byte[] content = IOUtils.toByteArray(is);
                    App.Version.Builder overrideVersion = App.Version.newBuilder();
                    JsonFormat.parser().merge(new String(content), overrideVersion);

                    if(cpuArch != Protocol.CpuArch.UNKNOWN && overrideVersion.hasLibVersion()) {
                        version.setLibVersion(overrideVersion.getLibVersion());
                    }

                    if(overrideVersion.getEntryBinariesCount() > 0) {
                        version.addAllEntryBinaries(overrideVersion.getEntryBinariesList());
                    }
                }
                outputArchives.get(cpuArch).setByArchVersionInfo(version.build());
            }
        }

        if(!isParsed()) {
            throw new IllegalFormatFlagsException("Cannot find package metadata");
        }
    }

    public void buildByArchPackage() throws IOException {
        if(!createBaseFolder()) {
            throw new IOException(String.format("Cannot found or create base directory for: %s, Abort.", appInfo.getAppName()));
        }

        ArchPackageBundle shareBundle = outputArchives.get(Protocol.CpuArch.UNKNOWN);
        for(Protocol.CpuArch cpuArch : outputArchives.keySet()) {
            if(cpuArch == Protocol.CpuArch.UNKNOWN) continue;
            ArchPackageBundle archPackageBundle = outputArchives.get(cpuArch);
            File targetFile = archPackageBundle.getOutput(appInfo.getAppName(), baseOutputDir);

            boolean addShare = false;
            if(shareBundle != null) {
                addShare = true;
                App.Version.Builder mergeVer = App.Version.newBuilder(archPackageBundle.getByArchVersionInfo());
                mergeVer.addAllEntryBinaries(shareBundle.getByArchVersionInfo().getEntryBinariesList());
                archPackageBundle.setByArchVersionInfo(mergeVer.build());
            }
            moveFolderContents(originalFile, targetFile, cpuArch, addShare, archPackageBundle.getByArchVersionInfo());
        }
    }

    private void moveFolderContents(File sourceTar, File destTar, Protocol.CpuArch targetCpuArch, boolean moveShare, App.Version version) throws IOException {
        String targetFolder = targetCpuArch.toString();
        String folderPrefix = targetFolder.endsWith("/") ? targetFolder : targetFolder + "/";
        String sharePrefix = SHARE_ARCH_PATH + "/";

        try (TarArchiveInputStream tais = new TarArchiveInputStream(new BufferedInputStream(new FileInputStream(sourceTar)));
             TarArchiveOutputStream taos = new TarArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(destTar)))) {

            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry;

            while ((entry = tais.getNextEntry()) != null) {
                String entryName = entry.getName();
                if(entryName.endsWith(METADATA_FILE)) continue;

                if (entryName.startsWith(folderPrefix) && !entryName.equals(folderPrefix)) {
                    mvEntity(entryName, folderPrefix, entry, taos, tais);
                } else if(moveShare && entryName.startsWith(sharePrefix) && !entryName.equals(sharePrefix)) {
                    mvEntity(entryName, sharePrefix, entry, taos, tais);
                }
            }

            byte[] contentBytes = JsonFormat.printer().print(version).getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry newMetadataEntry = new TarArchiveEntry(METADATA_FILE);
            newMetadataEntry.setSize(contentBytes.length);

            taos.putArchiveEntry(newMetadataEntry);
            taos.write(contentBytes);
            taos.closeArchiveEntry();
            taos.finish();
        }
    }

    private void mvEntity(String entryName, String folderPrefix, TarArchiveEntry entry, TarArchiveOutputStream taos, TarArchiveInputStream tais) throws IOException {
        String newName = entryName.substring(folderPrefix.length());
        TarArchiveEntry newEntry = new TarArchiveEntry(newName);

        newEntry.setSize(entry.getSize());
        newEntry.setMode(entry.getMode());
        newEntry.setModTime(entry.getModTime());
        taos.putArchiveEntry(newEntry);

        if (!entry.isDirectory()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = tais.read(buffer)) != -1) {
                taos.write(buffer, 0, len);
            }
        }
        taos.closeArchiveEntry();
    }

    private boolean createBaseFolder() {
        baseOutputDir = new File(Service.getInstance().getArgument().baseDir, String.format("%s/%s/%s",
                APP_PATH, appInfo.getAppName(), baseVersionInfo.getVersionName()));
        return baseOutputDir.isDirectory() || baseOutputDir.mkdirs();
    }

    private Protocol.CpuArch getArchByTag(String tag) {
        return switch (tag.toLowerCase().replace("/", "")) {
            case SHARE_ARCH_PATH -> Protocol.CpuArch.UNKNOWN;
            case "i386" -> Protocol.CpuArch.i386;
            case "x86_64" -> Protocol.CpuArch.x86_64;
            case "arm" -> Protocol.CpuArch.arm;
            case "aarch64" -> Protocol.CpuArch.aarch64;
            case "riscv32" -> Protocol.CpuArch.riscv32;
            case "riscv64" -> Protocol.CpuArch.riscv64;
            case "mipsel" -> Protocol.CpuArch.mipsel;
            case "mips64el" -> Protocol.CpuArch.mips64el;
            default -> throw new IllegalStateException("Unexpected value: " + tag);
        };
    }
}
