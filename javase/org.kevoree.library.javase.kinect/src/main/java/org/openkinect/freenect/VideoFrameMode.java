package org.openkinect.freenect;

import com.sun.jna.Structure;


/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 12/08/11
 * Time: 13:46
 */
public class VideoFrameMode extends Structure {
	public int reserved;
	public int resolution;
//	private UnionFormat format;
	public int bytes;
	public short width;
	public short height;
	public short dataBitsPerPixel;
	public short paddingBitsPerPixel;
	public short framerate;
	public short valid;

	/*private class UnionFormat extends Union {
		private int dummy;
		private VideoFormat video_format;
		private DepthFormat depth_format;
		

	}*/

	public VideoFrameMode () {
	}

	public VideoFrameMode (int reserved, int resolution/*, UnionFormat format*/, int bytes, short width, short height,
			short dataBitsPerPixel, short paddingBitsPerPixel, short framerate, short valid) {
		this.reserved = reserved;
		this.resolution = resolution;
//		this.format = format;
		this.bytes = bytes;
		this.width = width;
		this.height = height;
		this.dataBitsPerPixel = dataBitsPerPixel;
		this.paddingBitsPerPixel = paddingBitsPerPixel;
		this.framerate = framerate;
		this.valid = valid;
	}


	public static class VideoFrameModeByReference extends VideoFrameMode implements Structure.ByReference {

	}
	public static class VideoFrameModeByValue extends VideoFrameMode implements Structure.ByValue {

	}
}

/*typedef struct {
	uint32_t reserved;              *//**< unique ID used internally.  The meaning of values may change without notice.  Don't touch or depend on the contents of this field.  We mean it. *//*
	freenect_resolution resolution; *//**< Resolution this freenect_frame_mode describes, should you want to find it again with freenect_find_*_frame_mode(). *//*
	union {
		int32_t dummy;
		freenect_video_format video_format;
		freenect_depth_format depth_format;
	};                              *//**< The video or depth format that this freenect_frame_mode describes.  The caller should know which of video_format or depth_format to use, since they called freenect_get_*_frame_mode() *//*
	int32_t bytes;                  *//**< Total buffer size in bytes to hold a single frame of data.  Should be equivalent to width * height * (data_bits_per_pixel+padding_bits_per_pixel) / 8 *//*
	int16_t width;                  *//**< Width of the frame, in pixels *//*
	int16_t height;                 *//**< Height of the frame, in pixels *//*
	int8_t data_bits_per_pixel;     *//**< Number of bits of information needed for each pixel *//*
	int8_t padding_bits_per_pixel;  *//**< Number of bits of padding for alignment used for each pixel *//*
	int8_t framerate;               *//**< Approximate expected frame rate, in Hz *//*
	int8_t is_valid;                *//**< If 0, this freenect_frame_mode is invalid and does not describe a supported mode.  Otherwise, the frame_mode is valid. *//*
} freenect_frame_mode;*/
