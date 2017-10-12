package net.imagej.circleskinner.hough;

import java.util.List;

import org.scijava.Cancelable;

import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;

public interface HoughCircleDetectorOp< T > extends UnaryFunctionOp< RandomAccessibleInterval< T >, List< HoughCircle > >, Cancelable
{

}
