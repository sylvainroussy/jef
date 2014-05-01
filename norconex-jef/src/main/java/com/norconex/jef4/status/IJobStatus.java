package com.norconex.jef4.status;

import java.io.Serializable;
import java.util.Date;

import com.norconex.commons.lang.map.Properties;

public interface IJobStatus extends Serializable {

    String getJobName();
    JobState getState();
    double getProgress();
    String getNote();
    int getResumeAttempts();
    JobDuration getDuration();
    Date getLastActivity();
    Properties getProperties();
}
