package com.sample.hrv.demo;

import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView.Renderer;

import android.content.Context;
import android.os.SystemClock;


import com.sample.hrv.R;
import com.sample.hrv.sensor.BleHeartRateSensor;
import com.sample.hrv.sensor.BleSensor;

import static com.sample.hrv.demo.DemoHeartRateSensorActivity.HRVCalc.pNN50;
import static com.sample.hrv.demo.DemoHeartRateSensorActivity.HRVCalc.rMSSD;

import java.io.File;

/**
 * Created by Amanda Watson on 6/9/2015.
 */
public class DemoHeartRateSensorActivity extends DemoSensorActivity {
	private final static String TAG = DemoHeartRateSensorActivity.class
			.getSimpleName();

	private TextView viewText;
	private PolygonRenderer renderer;

	private GLSurfaceView view;

	//Data fo the array
	LinkedList<float[]> hrvData = new LinkedList<float[]>();
	int index = 0;
	boolean everyOther = false;
	float prevValue = 0f;
	int timeSum = 0;

	//Varibles to calculate HRV
	private float AVNN;
	private double SDNN;
	private double rMSSD;
	private float NN50;
	private float pNN50;

	//Stress level
	private int stressLevel;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.demo_opengl);
		view = (GLSurfaceView) findViewById(R.id.gl);

		getActionBar().setTitle(R.string.title_demo_heartrate);

		viewText = (TextView) findViewById(R.id.text);
		renderer = new PolygonRenderer(this);
		view.setRenderer(renderer);
		//view.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
		// Render when hear rate data is updated
		view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	@Override
	public void onDataRecieved(BleSensor<?> sensor, String text) {
		//String path = context.getFilesDir().getAbsolutePath();
		//File file = new File(path + "/my-file-name.txt");
		//String filename = "myfile";
		//String string = "Hello world!";
		//FileOutputStream outputStream;

		/*try {
			outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
			outputStream.write(string.getBytes());
			outputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}*/
		if (sensor instanceof BleHeartRateSensor) {
			final BleHeartRateSensor heartSensor = (BleHeartRateSensor) sensor;
			float[] values = heartSensor.getData();
			if(everyOther) {
				//Do not add when heart rate 0
				if (timeSum < 50000){
					if (values[1] != -1) {
						//Do not add when >20% off from previous value
						Log.i("Difference : ", "" + (values[1] - prevValue) / ((values[1] + prevValue) / 2));
						if ((values[1] - prevValue) / ((values[1] + prevValue) / 2) <= 0.2 && (values[1] - prevValue) / ((values[1] + prevValue) / 2) >= -0.8) {
							hrvData.add(values);
							index++;
							everyOther = false;
							timeSum += values[1];
							Log.i("Heart Rate Array", "" + values[0] + " " + values[1]);
						}
					}
					prevValue = values[1];
				}
				else {
					float[] arr = hrvData.getFirst();
					timeSum -= arr[1];
					hrvData.removeFirst();

					//Do HRV Calculations
					AVNN = HRVCalc.AVNN(hrvData);
					SDNN = HRVCalc.SDNN(hrvData);
					rMSSD = HRVCalc.rMSSD(hrvData);
					NN50 = HRVCalc.NN50(hrvData);
					pNN50 = HRVCalc.pNN50(hrvData);
				}
			}
			else{
				everyOther = true;
			}

			//If statements to determine real time stress level
			if(pNN50 > 0) {
				if (pNN50 < 1.9) {
					stressLevel = 10;
				} else if (pNN50 < 3.9) {
					stressLevel = 9;
				} else if (pNN50 < 5.9) {
					stressLevel = 8;
				} else if (pNN50 < 8.9) {
					stressLevel = 7;
				} else if (pNN50 < 10.9) {
					stressLevel = 6;
				} else if (pNN50 < 12.9) {
					stressLevel = 5;
				} else if (pNN50 < 14.9) {
					stressLevel = 4;
				} else if (pNN50 < 20.9) {
					stressLevel = 3;
				} else if (pNN50 < 26.9) {
					stressLevel = 2;
				} else if (pNN50 < 32.9) {
					stressLevel = 1;
				}
			}


			renderer.setInterval(values);
			view.requestRender();

			text = text + "\nAVNN = " + Float.toString(AVNN);
			text = text + "\nSDNN = " + Double.toString((Math.round(SDNN * 100)) / 100);
			text = text + "\nrMSSD = " +Double.toString((Math.round(rMSSD * 100)) / 100);
			text = text + "\nNN50 = " + Float.toString(NN50);
			text = text + "\npNN50 = " + Float.toString(pNN50);
			text = text + "\nTime = " + timeSum;
			text = text + "\nStress Level = " + stressLevel;
			//Log.i("text : ", "" + text);
			viewText.setText(text);
		}

	}

	/**
	 * This class provides the more in depth calculations for HRV. These Include
	 * 1)AVNN - Mean of beat to beat intervals
	 * 2)SDNN - Standard Deviations of the NN(normal-to-normal) intervals - 24 hours
	 * 3)SDNN Index - SDNN for a 5 minute index troughout the day - 5 minutes
	 * 4)RMSSD - Square root of the mean squared differences of successive NN intervals
	 * 5)TP - Total Power - Short term estimate of the total power of the power spectral
	 * 		density in the range of frequencies between 1 and .4 HZ
	 * 6)VLF - Very Low Frequency - 0.0033 nd 0.04 HZ
	 * 7)LF - Low Frequency - 0.04 and 0.15 HZ
	 * 8)HF - 0.15 ad 0.4 HZ
	 * 9)LF/HF Ratio - overall balance in sympathetic and parasymphathetic nervous systems
	 * 10)LF Norm - Normalized Low Frequency
	 * 11)HF Norm - Normalized High Frequency
	 */
	public static class HRVCalc{
		/** Time Domain Features**/

		/**
		 * AVNN - Mean of the beat to beat intervals
		 * @param ll - linked list with heart rate data
		 * @return AVNN calculation
		 */
		public static float AVNN(LinkedList<float[]> ll){
			float sum = 0f;
			float AVNN = 0f;

			for(int i = 0; i < ll.size(); i++){
				float arr[] = ll.get(i);
				sum += arr[1];
			}

			AVNN = (1.0f / (float)ll.size()) * sum;

			return AVNN;
		}

		/**
		 * SDNN - Standard deviation of NN intervals
		 * @param ll - linked list with the heart rate data
		 * @return SDNN calculation
		 */
		public static double SDNN(LinkedList<float[]> ll){
			double sum = 0;
			double SDNN = 0;

			float AVNN = AVNN(ll);

			for(int i = 0; i < ll.size(); i++){
				float arr[] = ll.get(i);
				sum += Math.pow((double)(arr[1]-AVNN),2.0);
			}
			SDNN = Math.sqrt((1.0f/(float)ll.size())*sum);

			return SDNN;
		}

		/**
		 * rMSSD - Square root of the mean squared difference of successive NN intervals
		 * @param ll - linked list the heart rate data
		 * @return rMSSD calculations
		 */
		public static double rMSSD(LinkedList<float[]> ll){
			double sum = 0;
			double rMSSD = 0;

			for(int i = 0; i < ll.size() - 1; i++){
				float arr[] = ll.get(i);
				float nextArr[] = ll.get(i+1);
				sum += Math.pow((nextArr[1] - arr[1]),2);
			}
			rMSSD = Math.sqrt((1.0f/(float)(ll.size() - 1))* sum);

			return rMSSD;
		}

		/**
		 * NN50 - number of pairs of successive RR's that differ by mre that 50 seconds
		 * @param ll - linked list with the heart rate data
		 * @return pNN50 calculation
		 */
		public static int NN50(LinkedList<float[]> ll){
			int n50Count = 0;

			for(int i = 0; i < ll.size() - 1; i++){
				float arr[] = ll.get(i);
				float nextArr[] = ll.get(i+1);
				if(nextArr[1] - arr[1] > 50){
					n50Count++;
				}
			}

			return n50Count;
		}

		/**
		 * pNN50 - number of pairs of successive RR's that differ by mre that 50 seconds
		 * @param ll - linked list with the heart rate data
		 * @return pNN50 calculation
		 */
		public static float pNN50(LinkedList<float[]> ll){
			int n50Count = NN50(ll);
			float pNN50 = 0f;

			pNN50 = (n50Count/(float)ll.size()) * 100f;

			return pNN50;
		}

		/**Frequency Domain Features**/




	}

	public abstract class AbstractRenderer implements GLSurfaceView.Renderer {
		
		public int[] getConfigSpec() {
			int[] configSpec = { EGL10.EGL_DEPTH_SIZE, 0, EGL10.EGL_NONE };
			return configSpec;
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
			gl.glDisable(GL10.GL_DITHER);
			gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
			gl.glClearColor(.5f, .5f, .5f, 1);
			gl.glShadeModel(GL10.GL_SMOOTH);
			gl.glEnable(GL10.GL_DEPTH_TEST);
		}

		public void onSurfaceChanged(GL10 gl, int w, int h) {
			gl.glViewport(0, 0, w, h);
			float ratio = (float) w / h;
			gl.glMatrixMode(GL10.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glFrustumf(-ratio, ratio, -1, 1, 3, 7);
		}

		public void onDrawFrame(GL10 gl) {
			gl.glDisable(GL10.GL_DITHER);
			gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
			gl.glMatrixMode(GL10.GL_MODELVIEW);
			gl.glLoadIdentity();
			GLU.gluLookAt(gl, 0, 0, -5, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
			gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
			draw(gl);
		}

		protected abstract void draw(GL10 gl);
	}

	public class PolygonRenderer extends AbstractRenderer {
		private final String TAG = PolygonRenderer.class
				.getSimpleName();

		// Number of points or vertices we want to use
		private final static int VERTS = 4;

		// A raw native buffer to hold the point coordinates
		private FloatBuffer mFVertexBuffer;

		// A raw native buffer to hold indices
		// allowing a reuse of points.
		private ShortBuffer mIndexBuffer;

		private int numOfIndecies = 0;

		private long prevtime = SystemClock.uptimeMillis();

		private int sides = 32;

		private float[] interval = { 0, 0, 0 };
		private float previousInterval = 0;

		public void setInterval(float[] interval) {
			if (this.interval[1] >= 0 && interval[1] > 0) {
				this.previousInterval = this.interval[1];
			}
			this.interval[0] = interval[0]; // heart rate
			this.interval[1] = interval[1]; // beat to beat interval
			this.interval[2] = 0;			// empty
		}
		
		public PolygonRenderer(Context context) {
			prepareBuffers(sides, interval[1]);
		}

		private void prepareBuffers(int sides, float radius) {
			Log.d(TAG,"radius: "+radius +" previous: "+previousInterval);
			// Is it a valid value?
			if (radius < 0) {
				radius = previousInterval;
			}
			
			// Double check if the previous value was valid
			if (radius < 0) {
				radius = 700;
			}
			Log.d(TAG,"final radius: "+radius);
			
			radius = ( ( radius / 1000 ) - 0.7f ) * 2;
			
			RegularPolygon t = new RegularPolygon(0, 0, 0, radius, sides);
			this.mFVertexBuffer = t.getVertexBuffer();
			this.mIndexBuffer = t.getIndexBuffer();
			this.numOfIndecies = t.getNumberOfIndecies();
			this.mFVertexBuffer.position(0);
			this.mIndexBuffer.position(0);
		}

		// overriden method
		protected void draw(GL10 gl) {
			long curtime = SystemClock.uptimeMillis();

			this.prepareBuffers(sides, interval[1]);
			gl.glColor4f(50/255.0f, 205/255.0f, 50/255.0f, 1.0f);
			gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
			gl.glDrawElements(GL10.GL_TRIANGLES, this.numOfIndecies,
					GL10.GL_UNSIGNED_SHORT, mIndexBuffer);
		}
	}


	private static class RegularPolygon {
		private static final String TAG = RegularPolygon.class
				.getSimpleName();
		private float cx, cy, cz, r;
		private int sides;
		private float[] xarray = null;
		private float[] yarray = null;

		public RegularPolygon(float incx, float incy, float incz, // (x,y,z)
																	// center
				float inr, // radius
				int insides) // number of sides
		{
			cx = incx;
			cy = incy;
			cz = incz;
			r = inr;
			sides = insides;

			xarray = new float[sides];
			yarray = new float[sides];
			calcArrays();
		}

		private void calcArrays() {
			float[] xmarray = this.getXMultiplierArray();
			float[] ymarray = this.getYMultiplierArray();

			// calc xarray
			for (int i = 0; i < sides; i++) {
				float curm = xmarray[i];
				float xcoord = cx + r * curm;
				xarray[i] = xcoord;
			}
			//this.printArray(xarray, "xarray");

			// calc yarray
			for (int i = 0; i < sides; i++) {
				float curm = ymarray[i];
				float ycoord = cy + r * curm;
				yarray[i] = ycoord;
			}
			//this.printArray(yarray, "yarray");

		}

		public FloatBuffer getVertexBuffer() {
			int vertices = sides + 1;
			int coordinates = 3;
			int floatsize = 4;
			int spacePerVertex = coordinates * floatsize;

			ByteBuffer vbb = ByteBuffer.allocateDirect(spacePerVertex
					* vertices);
			vbb.order(ByteOrder.nativeOrder());
			FloatBuffer mFVertexBuffer = vbb.asFloatBuffer();

			// Put the first coordinate (x,y,z:0,0,0)
			mFVertexBuffer.put(0.0f); // x
			mFVertexBuffer.put(0.0f); // y
			mFVertexBuffer.put(0.0f); // z

			int totalPuts = 3;
			for (int i = 0; i < sides; i++) {
				mFVertexBuffer.put(xarray[i]); // x
				mFVertexBuffer.put(yarray[i]); // y
				mFVertexBuffer.put(0.0f); // z
				totalPuts += 3;
			}
			//Log.d(TAG, "total puts: " + Integer.toString(totalPuts));
			return mFVertexBuffer;
		}

		public ShortBuffer getIndexBuffer() {
			short[] iarray = new short[sides * 3];
			ByteBuffer ibb = ByteBuffer.allocateDirect(sides * 3 * 2);
			ibb.order(ByteOrder.nativeOrder());
			ShortBuffer mIndexBuffer = ibb.asShortBuffer();
			for (int i = 0; i < sides; i++) {
				short index1 = 0;
				short index2 = (short) (i + 1);
				short index3 = (short) (i + 2);
				if (index3 == sides + 1) {
					index3 = 1;
				}
				mIndexBuffer.put(index1);
				mIndexBuffer.put(index2);
				mIndexBuffer.put(index3);

				iarray[i * 3 + 0] = index1;
				iarray[i * 3 + 1] = index2;
				iarray[i * 3 + 2] = index3;
			}
			//this.printShortArray(iarray, "index array");
			return mIndexBuffer;
		}

		private float[] getXMultiplierArray() {
			float[] angleArray = getAngleArrays();
			float[] xmultiplierArray = new float[sides];
			for (int i = 0; i < angleArray.length; i++) {
				float curAngle = angleArray[i];
				float sinvalue = (float) Math.cos(Math.toRadians(curAngle));
				float absSinValue = Math.abs(sinvalue);
				if (isXPositiveQuadrant(curAngle)) {
					sinvalue = absSinValue;
				} else {
					sinvalue = -absSinValue;
				}
				xmultiplierArray[i] = this.getApproxValue(sinvalue);
			}
			//this.printArray(xmultiplierArray, "xmultiplierArray");
			return xmultiplierArray;
		}

		private float[] getYMultiplierArray() {
			float[] angleArray = getAngleArrays();
			float[] ymultiplierArray = new float[sides];
			for (int i = 0; i < angleArray.length; i++) {
				float curAngle = angleArray[i];
				float sinvalue = (float) Math.sin(Math.toRadians(curAngle));
				float absSinValue = Math.abs(sinvalue);
				if (isYPositiveQuadrant(curAngle)) {
					sinvalue = absSinValue;
				} else {
					sinvalue = -absSinValue;
				}
				ymultiplierArray[i] = this.getApproxValue(sinvalue);
			}
			//this.printArray(ymultiplierArray, "ymultiplierArray");
			return ymultiplierArray;
		}

		private boolean isXPositiveQuadrant(float angle) {
			if ((0 <= angle) && (angle <= 90)) {
				return true;
			}

			if ((angle < 0) && (angle >= -90)) {
				return true;
			}
			return false;
		}

		private boolean isYPositiveQuadrant(float angle) {
			if ((0 <= angle) && (angle <= 90)) {
				return true;
			}

			if ((angle < 180) && (angle >= 90)) {
				return true;
			}
			return false;
		}

		private float[] getAngleArrays() {
			float[] angleArray = new float[sides];
			float commonAngle = 360.0f / sides;
			float halfAngle = commonAngle / 2.0f;
			float firstAngle = 360.0f - (90 + halfAngle);
			angleArray[0] = firstAngle;

			float curAngle = firstAngle;
			for (int i = 1; i < sides; i++) {
				float newAngle = curAngle - commonAngle;
				angleArray[i] = newAngle;
				curAngle = newAngle;
			}
			//printArray(angleArray, "angleArray");
			return angleArray;
		}

		private float getApproxValue(float f) {
			if (Math.abs(f) < 0.001) {
				return 0;
			}
			return f;
		}

		public int getNumberOfIndecies() {
			return sides * 3;
		}

		private void printArray(float array[], String tag) {
			StringBuilder sb = new StringBuilder(tag);
			for (int i = 0; i < array.length; i++) {
				sb.append(";").append(array[i]);
			}
			Log.d(TAG, sb.toString());
		}

		private void printShortArray(short array[], String tag) {
			StringBuilder sb = new StringBuilder(tag);
			for (int i = 0; i < array.length; i++) {
				sb.append(";").append(array[i]);
			}
			Log.d(TAG, sb.toString());
		}
	}

}
