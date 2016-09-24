package de.ferienakademie.smartquake.excitation;

/**
 * Created by user on 21.09.2016.
 */

public interface AccelerationProvider {

    /**
     * has to inform the listener
     * @return first element acceleration in X axis, second element acceleration in Y axis
     */
    double[] getAcceleration();

    /**
     * has to inform the listener
     * @return datastructure with timestamp, X axis acceleration, Y axis acceleration
     */
    AccelData getAccelerationMeasurement();

    /**
     *
     * @param timeStamp timeStamp at the beginning of the Simulation in nanoseconds
     * @param timeStep timeStep of the simulation in nanoseconds
     */
    void initTime(long timeStamp, double timeStep);
}
