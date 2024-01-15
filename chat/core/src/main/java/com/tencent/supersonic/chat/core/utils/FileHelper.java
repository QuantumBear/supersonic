package com.tencent.supersonic.chat.core.utils;

import static com.hankcs.hanlp.HanLP.Config.CustomDictionaryPath;

import com.hankcs.hanlp.dictionary.DynamicCustomDictionary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileHelper {

    public static final String FILE_SPILT = File.separator;

    public static void deleteCacheFile(String[] path) throws IOException {

        String customPath = getCustomPath(path);
        File customFolder = new File(customPath);

        File[] customSubFiles = getFileList(customFolder, ".bin");
        if (customSubFiles == null || customSubFiles.length == 0) {
            return;
        }

        for (File file : customSubFiles) {
            try {
                file.delete();
                log.info("customPath:{},delete file:{}", customPath, file);
            } catch (Exception e) {
                log.error("delete " + file, e);
            }
        }
    }

    private static File[] getFileList(File customFolder, String suffix) {
        List<File> fileList = new ArrayList<>();
        getFileListRecursive(customFolder, suffix, fileList);
        return fileList.toArray(new File[0]);
    }

    private static void getFileListRecursive(File folder, String suffix, List<File> fileList) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    getFileListRecursive(file, suffix, fileList);
                } else if (file.getName().toLowerCase().endsWith(suffix)) {
                    fileList.add(file);
                }
            }
        }
    }

    private static String getCustomPath(String[] path) {
        String lastParentDir = path[0].substring(0, path[0].lastIndexOf(FILE_SPILT)) + FILE_SPILT;
        return lastParentDir.substring(0, lastParentDir.lastIndexOf(FILE_SPILT)) + FILE_SPILT;
    }

    /**
     * reset path
     *
     * @param customDictionary
     */
    public static void resetCustomPath(DynamicCustomDictionary customDictionary) {
        String[] path = CustomDictionaryPath;

        String customPath = getCustomPath(path);
        File customFolder = new File(customPath);

        File[] customSubFiles = getFileList(customFolder, ".txt");

        List<String> fileList = new ArrayList<>();

        for (File file : customSubFiles) {
            if (file.isFile()) {
                fileList.add(file.getAbsolutePath());
            }
        }

        log.debug("CustomDictionaryPath:{}", fileList);
        CustomDictionaryPath = fileList.toArray(new String[0]);
        customDictionary.path = (CustomDictionaryPath == null || CustomDictionaryPath.length == 0) ? path
                : CustomDictionaryPath;
        if (CustomDictionaryPath == null || CustomDictionaryPath.length == 0) {
            CustomDictionaryPath = path;
        }
    }
}
