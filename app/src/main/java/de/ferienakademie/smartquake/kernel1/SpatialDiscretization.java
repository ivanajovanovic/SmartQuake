package de.ferienakademie.smartquake.kernel1;

import org.ejml.data.DenseMatrix64F;
import org.ejml.interfaces.linsol.ReducedRowEchelonForm;
import org.ejml.ops.CommonOps;

import java.util.List;

import de.ferienakademie.smartquake.eigenvalueProblems.GenEig;
import de.ferienakademie.smartquake.excitation.AccelerationProvider;
import de.ferienakademie.smartquake.managers.PreferenceReader;
import de.ferienakademie.smartquake.model.Beam;
import de.ferienakademie.smartquake.model.Node;
import de.ferienakademie.smartquake.model.Structure;

/**
 * Created by alex on 22.09.16.
 */
public class SpatialDiscretization {

    private DenseMatrix64F StiffnessMatrix;
    private DenseMatrix64F DampingMatrix;
    private DenseMatrix64F MassMatrix;
    private DenseMatrix64F InverseMassMatrix;
    private DenseMatrix64F LoadVector; // vector with the forces

    private DenseMatrix64F influenceVectorX;
    private DenseMatrix64F influenceVectorY;
    //private DenseMatrix64F DisplacementVector;  //project manager advice

    private double dampingCoefficient;

    //Modal Analysis part
    private DenseMatrix64F eigenvectorsmatrix;
    private DenseMatrix64F[] eigenvectors;
    private double[] eigenvalues;
    private DenseMatrix64F StiffnessVector;
    private DenseMatrix64F MassVector;
    private DenseMatrix64F DampingVector;
    private double[] ReducedEigenvalues;
    private DenseMatrix64F RedLoadVector;
    private  DenseMatrix64F[] Reducedeigenvectors;
    private DenseMatrix64F ReducedeigenvectorsMatrixTranspose;
    private DenseMatrix64F RedinfluenceVectorX;
    private DenseMatrix64F RedinfluenceVectorY;
    private DenseMatrix64F RedinfluenceVectorX_temp;
    private DenseMatrix64F RedinfluenceVectorY_temp;

    private int numberofDOF;

    Structure structure;
    // temporary vectors that will be scaled by acceleration
    private DenseMatrix64F influenceVectorX_temp;
    private DenseMatrix64F influenceVectorY_temp;

    public SpatialDiscretization(Structure structure) {
        this.structure = structure;
        //initialize displacement with zeros
        numberofDOF = structure.getNumberOfDOF();

        influenceVectorX = new DenseMatrix64F(getNumberofDOF(), 1);
        influenceVectorY = new DenseMatrix64F(getNumberofDOF(), 1);

        initializeMatrices();
        calculateInfluenceVector();
        //TODO: Fix eigenvalues with hinges
        calculateEigenvaluesAndVectors();

        dampingCoefficient = PreferenceReader.getDampingCoefficient();

        calculateDampingMatrix();
    }

    /**
     * return StiffnessMatrix
     */
    public DenseMatrix64F getStiffnessMatrix() {
        return StiffnessMatrix;
    }

    /**
     * return DampingMatrix
     */
    public DenseMatrix64F getDampingMatrix() {
        return DampingMatrix;
    }

    /**
     * return MassMatrix
     */
    public DenseMatrix64F getMassMatrix() {
        return MassMatrix;
    }

    /**
     * Calculate the stiffness, mass and damping matrices.
     */
    public void initializeMatrices() {
        StiffnessMatrix = new DenseMatrix64F(getNumberofDOF(), getNumberofDOF());
        MassMatrix = new DenseMatrix64F(getNumberofDOF(), getNumberofDOF());
        DampingMatrix = new DenseMatrix64F(getNumberofDOF(), getNumberofDOF());
        LoadVector = new DenseMatrix64F(getNumberofDOF(), 1);

        StiffnessMatrix.zero();
        MassMatrix.zero();


        calculateMassMatrix();
        calculateStiffnessMatrix();

    }


    public void calculateStiffnessMatrix() {
        for (int e = 0; e < structure.getBeams().size(); e++) {
            Beam beam = structure.getBeams().get(e);
            int[] dofs = beam.getDofs();
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 6; j++) {
                    StiffnessMatrix.add(dofs[i], dofs[j], beam.getElementStiffnessMatrix_globalized().get(i, j));
                }
            }
        }
        for (int i = 0; i < structure.getConDOF().size(); i++) {
            int j = structure.getConDOF().get(i);
            for (int k = 0; k < getNumberofDOF(); k++) {
                StiffnessMatrix.set(j, k, 0.0);
                StiffnessMatrix.set(k, j, 0.0);
            }
            StiffnessMatrix.set(j, j, 11.0);
        }
    }

    public void calculateMassMatrix() {
        for (int e = 0; e < structure.getBeams().size(); e++) {
            Beam beam = structure.getBeams().get(e);
            int[] dofs = beam.getDofs();
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 6; j++) {
                    MassMatrix.add(dofs[i], dofs[j], beam.getElementMassMatrix_globalized().get(i, j));
                }
            }
        }
        for (int i = 0; i < structure.getConDOF().size(); i++) {
            int j = structure.getConDOF().get(i);
            for (int k = 0; k < getNumberofDOF(); k++) {
                MassMatrix.set(j, k, 0.0);
                MassMatrix.set(k, j, 0.0);
            }
            MassMatrix.set(j, j, -1.0);
        }
    }

    public void calculateinverseMassMatrix() {
        if (!structure.isLumped()) {
            throw new RuntimeException("No diagonal mass matrix!");
        }
        InverseMassMatrix = new DenseMatrix64F(getNumberofDOF(), getNumberofDOF());
        InverseMassMatrix.zero();
        for (int e = 0; e < getNumberofDOF(); e++) {
            InverseMassMatrix.add(e, e, 1. / MassMatrix.get(e, e));
        }
    }

    public DenseMatrix64F getInverseMassMatrix() { // Only call for lumped cases.
        calculateinverseMassMatrix();
        return InverseMassMatrix;
    }

    public void calculateDampingMatrix() {
        DampingMatrix.zero();
        double omega1 = ReducedEigenvalues[0];
        double omega2 = ReducedEigenvalues[1];
        double a0 =  2 * dampingCoefficient * omega1 * omega2 / (omega1 + omega2);
        double a1 = 2 * dampingCoefficient / (omega1 + omega2);
        CommonOps.add(a0, MassMatrix, a1, StiffnessMatrix, DampingMatrix);
        for (int i = 0; i < structure.getConDOF().size(); i++) {
            int j = structure.getConDOF().get(i);
            for (int k = 0; k < getNumberofDOF(); k++) {
                DampingMatrix.set(j, k, 0.0);
                DampingMatrix.set(k, j, 0.0);
            }
            DampingMatrix.set(j, j, 1.0);
        }
    }


    public int getNumberofDOF() {
        return numberofDOF;
    }


    public Structure getStructure() {
        return structure;
    }


    public void setStructure(Structure structure) {
        this.structure = structure;
    }


    public void updateDisplacementsOfStructure(DenseMatrix64F displacementVector, double[] groundDisplacement) {

        // list of constrained dofs
        List<Integer> conDOF = structure.getConDOF();

        DenseMatrix64F displacementVector2 = displacementVector.copy();

        for (int k = 0; k < conDOF.size(); k++) {
            displacementVector2.set(conDOF.get(k), 0, 0);
        }

        for (int i = 0; i < structure.getNodes().size(); i++) {
            Node node = structure.getNodes().get(i);

            List<Integer> dofsOfNode = node.getDOF();

            for (int j = 0; j < dofsOfNode.size(); j++) {
                node.setSingleDisplacement(j, displacementVector2.get(dofsOfNode.get(j), 0));
            }
            node.saveTimeStepGroundDisplacement(groundDisplacement);
            node.saveTimeStepDisplacement();
        }

    }


    public DenseMatrix64F getLoadVector() {
        return LoadVector;
    }

    public void setLoadVector(DenseMatrix64F loadVector) {
        LoadVector = loadVector;
    }

    public void calculateInfluenceVector() {

        influenceVectorX.zero();
        influenceVectorY.zero();
        for (int i = 0; i < structure.getNodes().size(); i++) {
            Node node = structure.getNodes().get(i);
            List<Integer> DOF = node.getDOF();
            int DOFx = DOF.get(0);
            int DOFy = DOF.get(1);
            influenceVectorX.add(DOFx, 0, -1); //add influence vector in x-dir
            influenceVectorY.add(DOFy, 0, -1); //add influence vector in y-dir
        }

        influenceVectorX_temp = new DenseMatrix64F(influenceVectorX.getNumRows(), 1);
        influenceVectorY_temp = new DenseMatrix64F(influenceVectorY.getNumRows(), 1);

    }


    /**
     * Update the vector with forces using the acceleration values received from the {@link AccelerationProvider}
     *
     * @param acceleration - view {@link AccelerationProvider} for details
     */
    public void updateLoadVector(double[] acceleration) {
        if (PreferenceReader.includeGravity()) {

            CommonOps.scale(acceleration[0] - acceleration[2], influenceVectorX, influenceVectorX_temp);
            CommonOps.scale(acceleration[1] - acceleration[3], influenceVectorY, influenceVectorY_temp);
            CommonOps.addEquals(influenceVectorX_temp, influenceVectorY_temp);
            CommonOps.mult(MassMatrix, influenceVectorX_temp, LoadVector);
        } else {
            CommonOps.scale(acceleration[0], influenceVectorX, influenceVectorX_temp);
            CommonOps.scale(acceleration[1], influenceVectorY, influenceVectorY_temp);
            CommonOps.addEquals(influenceVectorX_temp, influenceVectorY_temp);
            CommonOps.mult(MassMatrix, influenceVectorX_temp, LoadVector);
        }


    }


    public void updateLoadVectorModalAnalyis(double[] acceleration) {

        RedLoadVector = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);
        if (PreferenceReader.includeGravity()) {

            CommonOps.scale(acceleration[0], RedinfluenceVectorX, RedinfluenceVectorX_temp); //influenceVectorX_temp
            CommonOps.scale(acceleration[1], RedinfluenceVectorY, RedinfluenceVectorY_temp);
            CommonOps.addEquals(RedinfluenceVectorX_temp, RedinfluenceVectorY_temp);
        } else {
            CommonOps.scale(acceleration[0], RedinfluenceVectorX, RedinfluenceVectorX_temp);
            CommonOps.scale(acceleration[1], RedinfluenceVectorY, RedinfluenceVectorY_temp);
            CommonOps.addEquals(RedinfluenceVectorX_temp, RedinfluenceVectorY_temp);
        }
        CommonOps.mult(ReducedeigenvectorsMatrixTranspose, influenceVectorX, RedLoadVector);
    }


    public void calculateEigenvaluesAndVectors() {
        GenEig eigen = new GenEig(StiffnessMatrix, MassMatrix); //solve GEN eigenvalues problem
        eigenvalues = eigen.getLambda();
        double[][] ev = eigen.getV();
        eigenvectorsmatrix = new DenseMatrix64F(ev);
        CommonOps.transpose(eigenvectorsmatrix, eigenvectorsmatrix); //transpose due to constructor of DenseMatrix64F in which rows and column are switched
        eigenvectors = CommonOps.columnsToVector(eigenvectorsmatrix, null);
        ReducedEigenvalues = new double[numberofDOF-structure.getConDOF().size()];

        Reducedeigenvectors = new DenseMatrix64F[numberofDOF-structure.getConDOF().size()];

        RedinfluenceVectorX = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);
        RedinfluenceVectorY = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);
        RedinfluenceVectorX_temp  = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);
        RedinfluenceVectorY_temp  = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);


        for (int i = 0; i < numberofDOF-structure.getConDOF().size(); i++) {
            Reducedeigenvectors[i]=new DenseMatrix64F(numberofDOF);
        }
        int counter =0;
        for (int i = 0; i < numberofDOF; i++) {

            if (eigenvalues[i]<0){
                continue;
            }else {
                ReducedEigenvalues[counter]=eigenvalues[i];
                Reducedeigenvectors[counter] = eigenvectors[i];
                RedinfluenceVectorX.set(counter,0,influenceVectorX.get(i,0));
                RedinfluenceVectorY_temp.set(counter,0,influenceVectorY_temp.get(i,0));
                RedinfluenceVectorX_temp.set(counter,0,influenceVectorX_temp.get(i,0));
                counter++;
            }
        }
        double[][] temporary = new double[numberofDOF-structure.getConDOF().size()][];
        for (int i = 0; i < temporary.length; ++i)
        {
            temporary[i] = Reducedeigenvectors[i].getData();
        }
        ReducedeigenvectorsMatrixTranspose = new DenseMatrix64F(temporary);


    }


    //Normalise eigenvectors
    public void normaliseEigenvectors() {
        for (int i = 0; i < getNumberofDOF(); i++) {
            CommonOps.scale(1 / Math.sqrt(MassMatrix.get(i, i)), eigenvectors[i]);
        }
    }



    public void getModalAnalysisMatrices(){
        normaliseEigenvectors();
        StiffnessVector = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);
        MassVector = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);
        DampingVector = new DenseMatrix64F(numberofDOF-structure.getConDOF().size(),1);

        for (int i = 0; i < numberofDOF-structure.getConDOF().size(); i++) {
            StiffnessVector.set(i,0,ReducedEigenvalues[i]);
            MassVector.set(i,0,1.0);
        }
        calculateDampingMatrix();
        for (int i = 0; i < numberofDOF-structure.getConDOF().size(); i++) {
           DampingVector.set(i,0,DampingMatrix.get(i,i));
        }


    }







    public void superimposeModalAnalyisSolutions(double[] modalSolutionvector, double[] groundDisplacement){
        DenseMatrix64F DisplacementVector = new DenseMatrix64F();
        DisplacementVector.zero();
        for (int i = 0; i < numberofDOF-structure.getConDOF().size(); i++) {
            CommonOps.add(Reducedeigenvectors[i],modalSolutionvector[i],DisplacementVector);
        }

        updateDisplacementsOfStructure(DisplacementVector, groundDisplacement);

    }

    public DenseMatrix64F getDampingVector() {
        return DampingVector;
    }

    public void setDampingVector(DenseMatrix64F dampingVector) {
        DampingVector = dampingVector;
    }

    public DenseMatrix64F getRedLoadVector() {
        return RedLoadVector;
    }

    public void setRedLoadVector(DenseMatrix64F redLoadVector) {
        RedLoadVector = redLoadVector;
    }

    public DenseMatrix64F getMassVector() {
        return MassVector;
    }

    public void setMassVector(DenseMatrix64F massVector) {
        MassVector = massVector;
    }

    public DenseMatrix64F getStiffnessVector() {
        return StiffnessVector;
    }

    public void setStiffnessVector(DenseMatrix64F stiffnessVector) {
        StiffnessVector = stiffnessVector;
    }
}
