// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of job states for functional services in the SIF Unity Broker.
 * 
 * <p>Job states track the execution lifecycle of functional service jobs.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for functional service job management.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum JobState {
    /**
     * Job has not started.
     * The job has been created but execution has not begun.
     */
    NOTSTARTED,
    
    /**
     * Job is currently executing.
     * The job is actively being processed.
     */
    INPROGRESS,
    
    /**
     * Job completed successfully.
     * The job has finished execution without errors.
     */
    COMPLETED,
    
    /**
     * Job failed.
     * The job encountered an error and could not complete successfully.
     */
    FAILED
}
