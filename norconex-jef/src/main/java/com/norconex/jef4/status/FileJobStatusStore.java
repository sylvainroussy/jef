/* Copyright 2010-2015 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.jef4.status;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.jef4.JEFException;
import com.norconex.jef4.JEFUtil;
import com.norconex.jef4.job.JobException;

/**
 * File-based status store. The created
 * file name matches the job id, plus the ".job" extension.  The path
 * where to locate the file depends on the constructor invoked.
 *
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public class FileJobStatusStore implements IJobStatusStore {

    private static final Logger LOG =
            LogManager.getLogger(FileJobStatusStore.class);

    private String jobdirLatest;
    private String jobdirBackupBase;
    private String statusDir;

    private boolean needToResolveDirs= true;
    
    public FileJobStatusStore() {
        super();
        this.needToResolveDirs = true;
    }
    /**
     * Creates a file-based job status serializer storing files in the given
     * job directory.
     * @param statusDir the base directory where to serialize the job status
     */
    public FileJobStatusStore(final String statusDir) {
        this.statusDir = statusDir;
        this.needToResolveDirs = true;
    }
    
    public String getStatusDirectory() {
        return statusDir;
    }
    public void setStatusDirectory(String statusDirectory) {
        this.statusDir = statusDirectory;
        this.needToResolveDirs = true;
    }

    private synchronized void resolveDirsIfNeeded() {
        // Leave now if we do not need to update dirs.
        if (!needToResolveDirs) {
            return;
        }

        // Status dir changed, update dirs
        String path = statusDir;
        if (StringUtils.isBlank(statusDir)) {
            LOG.info("No status directory specified.");
            path = JEFUtil.FALLBACK_WORKDIR.getAbsolutePath();
        } else {
            path = new File(path).getAbsolutePath();
        }
        LOG.debug("Status serialization directory: " + path); 
        jobdirLatest = path + File.separatorChar 
                + "latest" + File.separatorChar + "status";
        jobdirBackupBase = path + "/backup";
        File dir = new File(jobdirLatest);
        if (!dir.exists()) {
            try {
                FileUtils.forceMkdir(dir);
            } catch (IOException e) {
                throw new JEFException("Cannot create status directory: "
                        + dir, e);
            }
        }
        this.needToResolveDirs = false;
    }

    @Override
    public final void write(String suiteName, final IJobStatus jobStatus)
            throws IOException {

        File file = getStatusFile(suiteName, jobStatus.getJobId());
        if (file.createNewFile() && LOG.isDebugEnabled()) {
            LOG.debug("Created status file: " + file);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Writing status file: " + file);
        }
        Properties config = new Properties();
        config.setString("jobId", jobStatus.getJobId());
        config.setDouble("progress", jobStatus.getProgress());
        if (jobStatus.getNote() != null) {
            config.setString("note", jobStatus.getNote());
        }
        JobDuration duration = jobStatus.getDuration();
        if (jobStatus.getResumeAttempts() > 0) {
            config.setInt("resumeAttempts", jobStatus.getResumeAttempts());
            config.setDate("resumedStartTime", duration.getResumedStartTime());
            config.setDate("resumedLastActivity", 
                    duration.getResumedLastActivity());
        }
        if (duration.getStartTime() != null) {
            config.setDate("startTime", duration.getStartTime());
        }
        if (duration.getEndTime() != null) {
            config.setDate("endTime", duration.getEndTime());
        }
        if (jobStatus.isStopping() || jobStatus.isStopped()) {
            config.setBoolean("stopped", true);
        }
        Properties props = jobStatus.getProperties();
        for (String key : props.keySet()) {
            config.put("prop." + key, props.get(key));
        }
        
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
        config.store(os, "Status for job: " + jobStatus.getJobId());
        os.close();
    }

    @Override
    public final IJobStatus read(String suiteName, final String jobId)
            throws IOException {
        
        resolveDirsIfNeeded();
        MutableJobStatus jobStatus = new MutableJobStatus(jobId);
        File file = getStatusFile(suiteName, jobId);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Reading status file: " + file);
        }
        if (!file.exists()) {
            return jobStatus;
        }
        

        Properties config = new Properties();
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            config.load(is);
            if (LOG.isDebugEnabled()) {
                LOG.debug(jobId + " last active time: "
                        + new Date(file.lastModified()));
            }
            jobStatus.setLastActivity(new Date(file.lastModified()));
            
            jobStatus.setProgress(config.getDouble("progress", 0d));
            jobStatus.setNote(config.getString("note", null));
            jobStatus.setResumeAttempts(config.getInt("resumeAttempts", 0));
            
            JobDuration duration = new JobDuration();
            duration.setResumedStartTime(
                    config.getDate("resumedStartTime", null));
            duration.setResumedLastActivity(
                    config.getDate("resumedLastActivity", null));
            duration.setStartTime(config.getDate("startTime", null));
            duration.setEndTime(config.getDate("endTime", null));
            jobStatus.setDuration(duration);

            jobStatus.setStopRequested(config.getBoolean("stopped", false));
            
            Properties props = jobStatus.getProperties();
            for (String key : config.keySet()) {
                if (key.startsWith("prop.")) {
                    props.put(StringUtils.removeStart(
                            "prop.", key), props.get(key));
                }
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
        return jobStatus;
    }

    @Override
    public final void remove(final String suiteName, final String jobId)
            throws IOException {
        File file = getStatusFile(suiteName, jobId);
        FileUtil.delete(file);
    }

    @Override
    public final void backup(
            final String suiteName, final String jobId, final Date backupDate)
            throws IOException {
        File progressFile = getStatusFile(suiteName, jobId);
        File backupFile = getBackupFile(suiteName, jobId, backupDate);
        if (progressFile.exists()) {
            FileUtil.moveFile(progressFile, backupFile);
        }
    }

    @Override
    public long touch(String suiteName, String jobId) throws IOException {
        File file = getStatusFile(suiteName, jobId);
        FileUtils.touch(file);
        return file.lastModified();
    }
    
    /**
     * Gets the file used to store the job progress.
     * @param suiteName name space given to the job progress
     * @param jobId the job unique name
     * @return file used to store the job process
     */
    private File getStatusFile(final String suiteName, final String jobId) {
        resolveDirsIfNeeded();
        return new File(jobdirLatest 
                + "/" + FileUtil.toSafeFileName(suiteName)
                + "__" + FileUtil.toSafeFileName(jobId) + ".job");
    }
    /**
     * Gets the file used to store the job progress backup.
     * @param suiteName name space given to the job progress
     * @param jobId the id of the job
     * @param backupDate date used to timestamp to backup
     * @return file used to store the job process
     */
    private File getBackupFile(final String suiteName, final String jobId, 
            final Date backupDate) {
        resolveDirsIfNeeded();
        String date = new SimpleDateFormat(
                "yyyyMMddHHmmssSSSS").format(backupDate);
        File backupDir;
        try {
            backupDir = FileUtil.createDateDirs(
                    new File(jobdirBackupBase), backupDate);
        } catch (IOException e) {
            throw new JobException("Could not create backup directory for "
                    + "job \"" + jobId + "\".");
        }
        backupDir = new File(backupDir, "status");
        if (!backupDir.exists()) {
            try {
                FileUtils.forceMkdir(backupDir);
            } catch (IOException e) {
                throw new JEFException("Cannot create backup directory: "
                        + backupDir, e);
            }
        }
        return new File(backupDir + "/" + date + "__" 
                + FileUtil.toSafeFileName(suiteName)
                + "__" + FileUtil.toSafeFileName(jobId) + ".job");
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        setStatusDirectory(xml.getString("statusDir", statusDir));
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("statusStore");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("statusDir");
            writer.writeCharacters(new File(statusDir).getAbsolutePath());
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }       
    }

}
