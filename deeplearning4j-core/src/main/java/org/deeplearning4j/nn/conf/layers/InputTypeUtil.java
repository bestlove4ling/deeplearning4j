package org.deeplearning4j.nn.conf.layers;

import org.deeplearning4j.nn.conf.inputs.InputType;

/**
 * Utilities for calculating input types
 *
 * @author Alex Black
 */
public class InputTypeUtil {


    public static InputType getOutputTypeCnnLayers(InputType inputType, int[] kernelSize, int[] stride, int[] padding,
                                                   int outputDepth, String layerName) {

        InputType.InputTypeConvolutional i = (InputType.InputTypeConvolutional) inputType;
        int inHeight = i.getHeight();
        int inWidth = i.getWidth();
        int padH = padding[0];
        int padW = padding[1];
        int kH = kernelSize[0];
        int kW = kernelSize[1];
        int sH = stride[0];
        int sW = stride[1];

        if ((inHeight - kH + 2 * padH) % sH != 0) {
            throw new IllegalStateException("Invalid input configuration (layer name = \"" + layerName + "\") for height: inHeight=" + inHeight + ", kernelH="
                    + kH + ", padH=" + padH + ", strideH=" + sH + "; (" + inHeight + "-" + kH + "+2*" + padH + ")/" + sH
                    + " is not an integer");
        }
        if ((inWidth - kW + 2 * padW) % sW != 0) {
            throw new IllegalStateException("Invalid input configuration (layer name = \"" + layerName + "\") for width: inWidth=" + inWidth + ", kernelW="
                    + kW + ", padW=" + padW + ", strideW=" + sW + "; (" + inWidth + "-" + kW + "+2*" + padW + ")/" + sW
                    + " is not an integer");
        }

        int hOut = (inHeight - kH + 2 * padH) / sH + 1;
        int wOut = (inWidth - kW + 2 * padW) / sH + 1;
        return InputType.convolutional(hOut, wOut, outputDepth);
    }

}
