/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex JEF.
 * 
 * Norconex JEF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex JEF is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex JEF. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.jef4.job;

import com.norconex.jef4.suite.JobSuite;
import com.norconex.jef4.status.IJobStatus;
import com.norconex.jef4.status.JobStatusUpdater;

/**
 * <p>A job to be executed by the Job Execution Framework.
 * Implementors are responsible for reporting job progress and errors in a
 * uniform way.  Great care should be taken to make every jobs recoverable.</p>
 *
 * <p>Jobs are usually assembled together to form a <code>JobSuite</code>.
 * Progress tracking, error handling, logging, etc., are all handled by job
 * suites and implementors do not have to worry about these concerns when
 * creating jobs.</p>
 *
 * <p>To ensure the best integration possible with the framework, implementors
 * are invited to adhere to the following practices:
 * <ul>
 *   <li>update job progress on <code>JobProgress</code>;
 *   <li>ensure jobs are recoverable by considering the current progress
 *       passed to the execute method;
 *   <li>use Log4J for all logging purposes;
 *   <li>wrap exceptions you explicitly want to be handled by the framework
 *       into a runtime <code>JobException</code> instance;
 *   <li>use lazy-loading where possible
 * </ul>
 *
 * @author Pascal Essiembre
 */
public interface IJob {//<S extends IJobStatus> {

    
    /**
     * Gets the job unique name.
     * @return job unique name
     */
    String getName();

//    IJobStatus createInitialStatus(IJobStatus resumingStatus);

    
    /**
     * Executes this job.  Implementors are responsible for updating
     * execution progress on the given <code>JobProgress</code>.
     * @param statusUpdater status updater
     * @param suite job suite this job is part of
     */
    void execute(final JobStatusUpdater statusUpdater, final JobSuite suite);

    /**
     * Stops this job.  Implementors are responsible for terminating
     * the execution of this job.  The progress and other contextual 
     * information can be set, but the "status" should not 
     * be overwritten, as the framework will take care of assigning it.
     * @param status current job status
     * @param suite job suite this job is part of
     * @since 2.0
     */
    void stop(final IJobStatus status, final JobSuite suite);

}
