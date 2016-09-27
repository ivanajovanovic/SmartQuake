package de.ferienakademie.smartquake.kernel2;

import org.ejml.data.DenseMatrix64F;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.ferienakademie.smartquake.excitation.AccelerationProvider;
import de.ferienakademie.smartquake.kernel1.SpatialDiscretization;
import de.ferienakademie.smartquake.managers.PreferenceReader;

/**
 * Created by Felix Wechsler on 21/09/16.
 */
public class TimeIntegration {

    SpatialDiscretization spatialDiscretization;
    AccelerationProvider accelerationProvider;

    // total computed time between every time step. This variable prevents computing more than GUI wants
    double t;
    // time step
    double delta_t;
    //globale time since startSimulation
    double globalTime;

    // matrices of velocity
    DenseMatrix64F xDot;

    // provides the numerical algorithm for calculating the displacement
    TimeIntegrationSolver solver;

    // manages the multi threading
    ExecutorService executorService;



    /**
    * @param spatialDiscretization
    *          object to obtain all matrices, displacements, external forces
    *
    **/
    public TimeIntegration(SpatialDiscretization spatialDiscretization, AccelerationProvider accelerationProvider) {
        this.spatialDiscretization = spatialDiscretization;
        this.accelerationProvider = accelerationProvider;
    }


    /**
     * This method is called from the Simulation class to prepare everything for simulation
     */
    public void prepareSimulation(){

        //initial condition for the velocity.
        xDot = new DenseMatrix64F(spatialDiscretization.getNumberofDOF(),1);
        //This is just temporarily. In future this should choosen in the right way
        xDot.zero();

        //fixed step size
        delta_t = 0.015;

        //give the class the time step
        //accelerationProvider.initTime(delta_t*1e9);

        //stores the numerical scheme
        solver = new Newmark(spatialDiscretization, accelerationProvider, xDot,delta_t);
        //solver = new Euler(spatialDiscretization, accelerationProvider, xDot);

        //if modal analysis is activated we can diagonalize the matrices
        if(PreferenceReader.useModalAnalysis()) {
            spatialDiscretization.getModalAnalysisMatrices();
        }

        //for the parallel thread
        executorService = Executors.newSingleThreadExecutor();
    }

    public SimulationStep performSimulationStep() {
        return new SimulationStep().execute();
    }

    /**
     * Class that represents single simulation step of {@link TimeIntegration}.
     * If simulation step can not be performed during a single frame, it will be stopped.
     */
    public class SimulationStep {

        boolean isRunning;

        public SimulationStep execute() {
            isRunning = true;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    //reset time
                    t = 0;

                    //calculates time step

                    //update loadVector
                    double[] currExcitation = accelerationProvider.getAcceleration();

                    spatialDiscretization.updateLoadVector(currExcitation);

                    //get the loadVector for the whole calculation
                    solver.setFLoad(spatialDiscretization.getLoadVector());




                    //long firstTime = System.nanoTime();
                    while(t < 0.03-0.000001 && isRunning) {
                        //calculate new displacement
                        solver.nextStep(t, delta_t);
                        //add ground movement for recording
                        solver.setGroundPosition(delta_t, currExcitation);
                        t += delta_t;

                    }

                    //for the sensor team
                    globalTime += 0.03;

                    //for recording
                    //long secondTime = System.nanoTime();
                  //  Log.e("Timestamp",""+(secondTime-firstTime));

                    //update the displacement in the node variables
                    spatialDiscretization.updateDisplacementsOfStructure(solver.getX(), solver.getGroundPosition());

                    isRunning = false;
                }
            });
            return this;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public void stop() {
            isRunning = false;
        }

    }
}
