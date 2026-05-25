package com.staploy.server.admin.pkg;

import com.google.protobuf.util.JsonFormat;
import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Cpus;
import com.staploy.server.admin.AdminConst;
import com.staploy.server.commons.service.Service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AppPackage {
    public static long REQUIRE_MINIMUM_PACKAGE_VER = 1;
    private Admin.PackageHeader packageHeader;
    private File baseOutputDir;
    private File originalFile;
    private final HashMap<Cpus.CpuArch, ArchPackageBundle> outputArchives;

    public static class ArchPackageBundle {
        private final Cpus.CpuArch cpuArch;
        private App.Version byArchVersionInfo;
        private File outputArchive;

        public ArchPackageBundle(Cpus.CpuArch cpuArch, App.Version baseVersion) {
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

    public boolean isNotParsed() {
        return packageHeader == null;
    }

    @NotNull
    public App.AppInfo getAppInfo() {
        if(isNotParsed()) throw new NullPointerException("Package Not yet parsed: " + originalFile);
        return packageHeader.getPackageInfo().getApp();
    }

    @NotNull
    public App.Version getBaseVersionInfo() {
        if(isNotParsed()) throw new NullPointerException("Package Not yet parsed: " + originalFile);
        return packageHeader.getPackageInfo().getCurrentVersion();
    }

    public Set<Cpus.CpuArch> getAvailableArch() {
        return outputArchives.keySet();
    }

    public HashMap<Cpus.CpuArch, ArchPackageBundle> getOutputArchives() {
        return outputArchives;
    }

    public void parse() throws IllegalFormatException, IOException {
        packageHeader = null;
        outputArchives.clear();

        if(originalFile == null) throw new IllegalStateException("Parse target not specified");
        checkMeta : try (TarFile tarFile = new TarFile(originalFile)) {
            final List<TarArchiveEntry> tarArchiveEntries = tarFile.getEntries();
            TarArchiveEntry entry = tarArchiveEntries.stream()
                    .filter(e -> e.isFile() && e.getName().equals(AdminConst.METADATA_FILE))
                    .findFirst()
                    .orElse(null);

            if (entry != null) {
                try (InputStream is = tarFile.getInputStream(entry)) {
                    byte[] content = IOUtils.toByteArray(is);
                    Admin.PackageHeader.Builder headerBuilder = Admin.PackageHeader.newBuilder();
                    JsonFormat.parser().merge(new String(content), headerBuilder);
                    packageHeader = headerBuilder.build();

                    if(packageHeader.getFormatVersion() < REQUIRE_MINIMUM_PACKAGE_VER) {
                        throw new IllegalFormatFlagsException(
                                String.format("Package format version %d is lower than required version of this server (%d)",
                                packageHeader.getFormatVersion(), REQUIRE_MINIMUM_PACKAGE_VER));
                    }
                }
            } else break checkMeta;

            List<TarArchiveEntry> archEntries = tarArchiveEntries.stream()
                    .filter(e -> e.isDirectory() && e.getName().split("/").length == 1)
                    .toList();

            for(TarArchiveEntry archiveEntry : archEntries) {
                Cpus.CpuArch cpuArch = getArchByTag(archiveEntry.getName());
                outputArchives.put(cpuArch, new ArchPackageBundle(cpuArch, getBaseVersionInfo()));
            }

            List<TarArchiveEntry> byArchEntries = tarArchiveEntries.stream()
                    .filter(e -> e.isFile() && e.getName().endsWith(AdminConst.METADATA_FILE) && e.getName().split("/").length == 2)
                    .toList();

            for(TarArchiveEntry byArchMetadata : byArchEntries) {
                Cpus.CpuArch cpuArch = getArchByTag(byArchMetadata.getName().split("/")[0]);
                App.Version.Builder version = App.Version.newBuilder(outputArchives.get(cpuArch).getByArchVersionInfo());

                try (InputStream is = tarFile.getInputStream(byArchMetadata)) {
                    byte[] content = IOUtils.toByteArray(is);
                    App.Version.Builder overrideVersion = App.Version.newBuilder();
                    JsonFormat.parser().merge(new String(content), overrideVersion);

                    if(cpuArch != Cpus.CpuArch.UNKNOWN && overrideVersion.hasLibVersion()) {
                        version.setLibVersion(overrideVersion.getLibVersion());
                    }

                    if(overrideVersion.getEntryBinariesCount() > 0) {
                        version.addAllEntryBinaries(overrideVersion.getEntryBinariesList());
                    }
                }
                outputArchives.get(cpuArch).setByArchVersionInfo(version.build());
            }
        }

        if(isNotParsed()) {
            throw new IllegalFormatFlagsException("Cannot find package metadata");
        }
    }

    public void buildByArchPackage() throws IOException {
        if(!createBaseFolder()) {
            throw new IOException(String.format("Cannot found or create base directory for: %s, Abort.", getAppInfo().getAppName()));
        }

        ArchPackageBundle shareBundle = outputArchives.get(Cpus.CpuArch.UNKNOWN);
        for(Cpus.CpuArch cpuArch : outputArchives.keySet()) {
            if(cpuArch == Cpus.CpuArch.UNKNOWN) continue;
            ArchPackageBundle archPackageBundle = outputArchives.get(cpuArch);
            File targetFile = archPackageBundle.getOutput(getAppInfo().getAppName(), baseOutputDir);

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

    private void moveFolderContents(File sourceTar, File destTar, Cpus.CpuArch targetCpuArch, boolean moveShare, App.Version version) throws IOException {
        String targetFolder = targetCpuArch.toString();
        String folderPrefix = targetFolder.endsWith("/") ? targetFolder : targetFolder + "/";
        String sharePrefix = AdminConst.SHARE_ARCH_PATH + "/";

        try (TarArchiveInputStream tais = new TarArchiveInputStream(new BufferedInputStream(new FileInputStream(sourceTar)));
             TarArchiveOutputStream taos = new TarArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(destTar)))) {

            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry;

            while ((entry = tais.getNextEntry()) != null) {
                String entryName = entry.getName();
                if(entryName.endsWith(AdminConst.METADATA_FILE)) continue;

                if (entryName.startsWith(folderPrefix) && !entryName.equals(folderPrefix)) {
                    mvEntity(entryName, folderPrefix, entry, taos, tais);
                } else if(moveShare && entryName.startsWith(sharePrefix) && !entryName.equals(sharePrefix)) {
                    mvEntity(entryName, sharePrefix, entry, taos, tais);
                }
            }

            byte[] contentBytes = JsonFormat.printer().print(version).getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry newMetadataEntry = new TarArchiveEntry(AdminConst.METADATA_FILE);
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
                AdminConst.APP_PATH, getAppInfo().getAppName(), getBaseVersionInfo().getVersionName()));
        return baseOutputDir.isDirectory() || baseOutputDir.mkdirs();
    }

    private Cpus.CpuArch getArchByTag(String tag) {
        return switch (tag.toLowerCase().replace("/", "")) {
            case AdminConst.SHARE_ARCH_PATH -> Cpus.CpuArch.UNKNOWN;
            case "i386" -> Cpus.CpuArch.i386;
            case "x86_64" -> Cpus.CpuArch.x86_64;
            case "arm" -> Cpus.CpuArch.arm;
            case "aarch64" -> Cpus.CpuArch.aarch64;
            case "riscv32" -> Cpus.CpuArch.riscv32;
            case "riscv64" -> Cpus.CpuArch.riscv64;
            case "mipsel" -> Cpus.CpuArch.mipsel;
            case "mips64el" -> Cpus.CpuArch.mips64el;
            default -> throw new IllegalStateException("Unexpected value: " + tag);
        };
    }
}
