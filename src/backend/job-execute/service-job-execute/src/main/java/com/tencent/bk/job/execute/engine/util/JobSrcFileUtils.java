/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.execute.engine.util;

import com.google.common.collect.Sets;
import com.tencent.bk.job.common.model.dto.IpDTO;
import com.tencent.bk.job.common.util.file.PathUtil;
import com.tencent.bk.job.common.util.function.LambdasUtil;
import com.tencent.bk.job.execute.engine.consts.FileDirTypeConf;
import com.tencent.bk.job.execute.engine.model.FileDest;
import com.tencent.bk.job.execute.engine.model.JobFile;
import com.tencent.bk.job.execute.model.FileDetailDTO;
import com.tencent.bk.job.execute.model.FileSourceDTO;
import com.tencent.bk.job.execute.model.StepInstanceDTO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 源文件工具类
 */
public class JobSrcFileUtils {
    /**
     * 构造源文件路径与目标文件路径的映射关系
     *
     * @param srcFiles  源文件
     * @param targetDir 目标目录
     * @return 源文件路径与目标文件路径的映射关系
     */
    public static Map<String, FileDest> buildSourceDestPathMapping(Set<JobFile> srcFiles, String targetDir) {
        Map<String, FileDest> sourceDestPathMap = new HashMap<>();
        String standardTargetDir = FilePathUtils.standardizedDirPath(targetDir);
        long currentTime = System.currentTimeMillis();
        for (JobFile srcFile : srcFiles) {
            // 本地文件的源ip是本机ip，展开源文件IP地址宏采用"0.0.0.0"
            String destDirPath = MacroUtil.resolveFileSrcIpMacro(standardTargetDir, srcFile.isLocalUploadFile() ?
                "0_0.0.0.0" : srcFile.getCloudAreaIdAndIp());
            destDirPath = MacroUtil.resolveDate(destDirPath, currentTime);
            addSourceDestPathMapping(sourceDestPathMap, srcFile, destDirPath);
        }
        return sourceDestPathMap;
    }

    private static void addSourceDestPathMapping(Map<String, FileDest> sourceDestPathMap, JobFile sourceFile,
                                                 String destDirPath) {
        sourceDestPathMap.put(sourceFile.getFileUniqueKey(), buildFileDest(sourceFile, destDirPath));
    }

    private static FileDest buildFileDest(JobFile sourceFile, String destDirPath) {
        String destPath = "";
        if (sourceFile.isDir()) {
            destPath = FilePathUtils.appendDirName(destDirPath, FilePathUtils.parseDirName(sourceFile.getDir()));
        } else {
            destPath = FilePathUtils.appendFileName(destDirPath, sourceFile.getFileName());
        }
        return new FileDest(destPath, destDirPath, sourceFile.getFileName());
    }

    /**
     * 从步骤解析源文件
     *
     * @param stepInstance      步骤
     * @param localServerIp     job server ip
     * @param jobStorageRootDir job共享存储根目录
     * @return 源文件
     */
    public static Set<JobFile> parseSendFileList(StepInstanceDTO stepInstance, String localServerIp,
                                                 String jobStorageRootDir) {
        Set<JobFile> sendFiles = Sets.newTreeSet();
        for (FileSourceDTO fileSource : stepInstance.getFileSourceList()) {
            List<FileDetailDTO> files = fileSource.getFiles();
            if (!fileSource.isLocalUpload()) {
                boolean isThirdFile = false;
                Integer fileSourceId = fileSource.getFileSourceId();
                if (fileSourceId != null && fileSourceId > 0) {
                    // 第三方文件源文件
                    isThirdFile = true;
                }
                // 远程服务器文件分发
                Long accountId = fileSource.getAccountId();
                String accountAlias = fileSource.getAccountAlias();
                // 远程文件
                List<IpDTO> ipList = fileSource.getServers().getIpList();
                Set<String> invalidIpSet = stepInstance.getInvalidIps();
                for (FileDetailDTO file : files) {
                    String filePath = StringUtils.isNotEmpty(file.getResolvedFilePath()) ? file.getResolvedFilePath()
                        : file.getFilePath();
                    Pair<String, String> fileNameAndPath = FilePathUtils.parseDirAndFileName(filePath);
                    String dir = fileNameAndPath.getLeft();
                    String fileName = fileNameAndPath.getRight();
                    Predicate<IpDTO> predicate = LambdasUtil.not(ip -> invalidIpSet.contains(ip.convertToStrIp()));
                    for (IpDTO ipDTO : ipList) {
                        if (predicate.test(ipDTO)) {
                            if (isThirdFile) {
                                sendFiles.add(new JobFile(false, ipDTO.convertToStrIp(), filePath,
                                    file.getThirdFilePathWithFileSourceName(), dir, fileName,
                                    stepInstance.getAppId(), accountId, accountAlias));
                            } else {
                                sendFiles.add(new JobFile(false, ipDTO.convertToStrIp(), filePath,
                                    filePath, dir, fileName, stepInstance.getAppId(), accountId, accountAlias));
                            }
                        }
                    }

                }
            } else {
                // 本地文件
                for (FileDetailDTO file : files) {
                    Pair<String, String> fileNameAndPath = FilePathUtils.parseDirAndFileName(file.getFilePath());
                    String dir = NFSUtils.getFileDir(jobStorageRootDir, FileDirTypeConf.UPLOAD_FILE_DIR)
                        + fileNameAndPath.getLeft();
                    String fileName = fileNameAndPath.getRight();
                    sendFiles.add(new JobFile(true, IpHelper.fix1To0(localServerIp),
                        file.getFilePath(), dir, fileName, "root", null,
                        FilePathUtils.parseDirAndFileName(file.getFilePath()).getRight()));
                }
            }
        }
        return sendFiles;
    }

    /**
     * 构造源文件原始路径与显示路径的映射关系
     *
     * @param sourceFiles    源文件
     * @param localUploadDir 本地上传文件根目录
     * @return 源文件原始路径与显示路径的映射关系
     */
    public static Map<String, String> buildSourceFileDisplayMapping(Set<JobFile> sourceFiles, String localUploadDir) {
        Map<String, String> sourceFileDisplayMap = new HashMap<>();
        sourceFiles.forEach(sourceFile -> {
            Pair<String, String> pair = FilePathUtils.parseDirAndFileName(sourceFile.getFilePath());
            String standardPath = FilePathUtils.standardizedDirPath(pair.getLeft()) + pair.getRight();
            if (sourceFile.isLocalUploadFile() && !standardPath.startsWith(localUploadDir)) {
                sourceFileDisplayMap.put(PathUtil.joinFilePath(localUploadDir, standardPath),
                    sourceFile.getDisplayFilePath());
            } else {
                sourceFileDisplayMap.put(standardPath, sourceFile.getDisplayFilePath());
            }
        });
        return sourceFileDisplayMap;
    }
}
