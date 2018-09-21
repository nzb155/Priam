/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.priam.backup;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.netflix.priam.aws.S3BackupPath;
import com.netflix.priam.backup.AbstractBackupPath.BackupFileType;
import com.netflix.priam.config.IConfiguration;
import com.netflix.priam.merics.BackupMetrics;
import com.netflix.priam.notification.BackupNotificationMgr;
import org.json.simple.JSONArray;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;

@Singleton
public class FakeBackupFileSystem extends AbstractFileSystem {
    private List<AbstractBackupPath> flist;
    public Set<String> downloadedFiles;
    public Set<String> uploadedFiles;
    public String baseDir, region, clusterName;

    @Inject
    private
    Provider<S3BackupPath> pathProvider;

    @Inject
    public FakeBackupFileSystem(IConfiguration configuration, BackupMetrics backupMetrics,
                                BackupNotificationMgr backupNotificationMgr){
        super(configuration, backupMetrics, backupNotificationMgr);
    }

    public void setupTest(List<String> files) {
        clearTest();
        flist = new ArrayList<AbstractBackupPath>();
        for (String file : files) {
            S3BackupPath path = pathProvider.get();
            path.parseRemote(file);
            flist.add(path);
        }
        downloadedFiles = new HashSet<String>();
        uploadedFiles = new HashSet<String>();
    }

    public void setupTest() {
        clearTest();
        flist = new ArrayList<AbstractBackupPath>();
        downloadedFiles = new HashSet<String>();
        uploadedFiles = new HashSet<String>();
    }

    private void clearTest() {
        if (flist != null)
            flist.clear();
        if (downloadedFiles != null)
            downloadedFiles.clear();
    }

    public void addFile(String file) {
        S3BackupPath path = pathProvider.get();
        path.parseRemote(file);
        flist.add(path);
    }

    @Override
    public Iterator<AbstractBackupPath> list(String bucket, Date start, Date till) {
        String[] paths = bucket.split(String.valueOf(S3BackupPath.PATH_SEP));

        if (paths.length > 1) {
            baseDir = paths[1];
            region = paths[2];
            clusterName = paths[3];
        }

        List<AbstractBackupPath> tmpList = new ArrayList<AbstractBackupPath>();
        for (AbstractBackupPath path : flist) {

            if ((path.time.after(start) && path.time.before(till)) || path.time.equals(start)
                    && path.baseDir.equals(baseDir) && path.clusterName.equals(clusterName) && path.region.equals(region)) {
                tmpList.add(path);
            }
        }
        return tmpList.iterator();
    }

    public void shutdown() {
        //nop
    }

    @Override
    public long getFileSize(Path remotePath) throws BackupRestoreException {
        return 0;
    }

    @Override
    public Iterator<AbstractBackupPath> listPrefixes(Date date) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void cleanup() {
        // TODO Auto-generated method stub
    }

    @Override
    protected void downloadFileImpl(Path remotePath, Path localPath) throws BackupRestoreException {
        //if (path.type == BackupFileType.META)
        {
            // List all files and generate the file
            try (FileWriter fr = new FileWriter(localPath.toFile())) {
                JSONArray jsonObj = new JSONArray();
                for (AbstractBackupPath filePath : flist) {
                    if (filePath.type == BackupFileType.SNAP)
                        jsonObj.add(filePath.getRemotePath());
                }
                fr.write(jsonObj.toJSONString());
                fr.flush();
            } catch (IOException io) {
                throw new BackupRestoreException(io.getMessage(), io);
            }
        }
        downloadedFiles.add(remotePath.toString());
        System.out.println("Downloading " + remotePath.toString());
    }

    @Override
    protected long uploadFileImpl(Path localPath, Path remotePath) throws BackupRestoreException {
        uploadedFiles.add(localPath.toFile().getAbsolutePath());
        return localPath.toFile().length();
    }
}
