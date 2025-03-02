/*
    Copyright 2005 Martin Ruckert

    ruckertm@acm.org

    This file is part of mp32pcm.

    mp32pcm is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    mp32pcm is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with mp32pcm; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/
#include <fstream>
#include <cstdlib>                                                  /*  10 */
#define MP3_MIN_BUFFER (2*1152)                                      /*  15 */
#define MP3_CONTINUE 0                                               /*  26 */
#define MP3_SKIP   0x0100
#define MP3_REPEAT 0x0200
#define MP3_MUTE   0x0400
#define MP3_REPAIR 0x0800
#define MP3_BREAK 0x1000                                             /*  27 */
#define MP3_ERROR -1                                                 /*  28 */
#define MP3_ERROR_NO_INPUT -2                                        /*  40 */
#define MP3_ERROR_TOO_MANY -3                                        /*  45 */
#define MP3_ERROR_MEMORY -4                                          /*  47 */
#define MP3_ERROR_NO_ID -5                                           /*  54 */
#define MP3_ERROR_NOT_OPEN -6
#define MP3_ERROR_DONE -7                                            /*  57 */
#define MP3_ERROR_NO_BUFFER -8
#define MP3_ERROR_NO_SIZE -9
#define SCALEFACTOR_ERROR 0x0010                                     /* 114 */
#define MP3_EQ_UNITGAIN 210                                          /* 446 */


#define MAX_FRAME 1729                                               /* 170 */

typedef short int mp3_sample;                                        /*  14 */

class mp3_info {
    /*  23 */
    constexpr static const int bit_rate_table[3][3][16] = {                        /* 415 */
            {
                    {0, 32000, 64000, 96000, 128000, 160000, 192000, 224000, 256000, 288000,
                                                                                          320000, 352000, 384000, 416000, 448000, -1},
                    {0, 32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000, 192000,
                            224000, 256000, 320000, 384000, -1},
                    {0, 32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000,
                            192000, 224000, 256000, 320000, -1}},
            {
                    {0, 32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000,
                                                                                                  176000, 192000, 224000, 256000, -1},
                    {0, 8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000,
                            112000, 128000, 144000, 160000, -1},
                    {0, 8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000,
                            112000, 128000, 144000, 160000, -1}},
            {
                    {0, 32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000,
                                                                                                  176000, 192000, 224000, 256000, -1},
                    {0, 8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000,
                            112000, 128000, 144000, 160000, -1},
                    {0, 8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000,
                            112000, 128000, 144000, 160000, -1}},
    };

    constexpr static const int frequency_table[3][3] = {                           /* 416 */
            {44100, 48000, 32000},
            {22050, 24000, 16000},
            {11025, 12000, 8000}
    };
public:
    int id;
    unsigned int header;
    int version;                                                       /*  31 */
#define MP3_V1_0 0x00
#define MP3_V2_0 0x01
#define MP3_V2_5 0x02
    int layer;
    int crc_protected;                                                 /*  32 */
    int bit_rate;
    int frame_size;
    int frame_position;
    int samples;
    int private_;
    int mode;
#define MP3_STEREO       0x00
#define MP3_JOINT_STEREO 0x01
#define MP3_DUAL_CHANNEL 0x02
#define MP3_MONO         0x03
    int copyright;
    int original;
    int emphasis;
    int frame;
    int sample_rate;                                                   /*  33 */
    int channels;
    int bit_per_sample;
    int free_format;                                                   /*  84 */
    int frequency_index;                                               /*  86 */
    int padding;                                                       /*  89 */
    int bound;                                                         /*  96 */
    int changes;                                                       /* 182 */
    int ms_stereo;                                                     /* 282 */
    int i_stereo;
    int fixed_size;                                                    /* 431 */



     int
    decode_header ( unsigned char *frame)
    {                                                                    /*  78 */
        unsigned int header;

        header = (((((frame[0] << 8) | frame[1]) << 8) | frame[2]) << 8) | frame[3];
        this->header = header;
        {
            int n = 11;                                                      /*  79 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits != 0x7FF)
                return 0;
        }
        {
            int n = 2;                                                       /*  81 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits == 0)
                this->version = MP3_V2_5;
            else if (bits == 2)
                this->version = MP3_V2_0;
            else if (bits == 3)
                this->version = MP3_V1_0;
            else
                return 0;
        }
        {
            int n = 2;                                                       /*  82 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits == 0)
                return 0;
            this->layer = 4 - bits;
        }
        {
            int n = 1;                                                       /*  83 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->crc_protected = (bits == 0);
        }
        {
            int n = 4;                                                       /*  85 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits == 0)
                this->free_format = 1;
            else if (bits == 0xF)
                return 0;
            else {
                this->free_format = 0;
                this->bit_rate = bit_rate_table[this->version][this->layer - 1][bits];
            }
        }
        {
            int n = 2;                                                       /*  87 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits == 3)
                return 0;
            this->frequency_index = bits;
            this->sample_rate = frequency_table[this->version][this->frequency_index];
        }
        {
            int n = 1;                                                       /*  88 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->padding = bits;
        }
        {
            int n = 1;                                                       /*  92 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->private_ = bits;
        }
        {
            int n = 2;                                                       /*  94 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->mode = bits;
            if (this->mode == MP3_MONO)
                this->channels = 1;
            else
                this->channels = 2;
        }
        {
            int n = 2;                                                       /*  95 */

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->bound = bits * 4 + 4;                                      /*  97 */
            if (this->mode != MP3_JOINT_STEREO)
                this->bound = 32;
            if (this->mode == MP3_JOINT_STEREO) {                            /* 281 */
                this->ms_stereo = (bits >> 1) & 1;
                this->i_stereo = bits & 1;
            }
            else
                this->ms_stereo = this->i_stereo = 0;
        }
        {
            int n = 1;

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->copyright = bits;
        }
        {
            int n = 1;

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->original = bits;
        }
        {
            int n = 2;

            int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->emphasis = bits;
        }
        {
            if (this->layer == 1)                                            /*  90 */
                this->frame_size =
                        4 * (this->padding +
                             (384 / (8 * 4)) * this->bit_rate / this->sample_rate);
            else
                this->frame_size =
                        this->padding + (1152 / 8) * this->bit_rate / this->sample_rate;
        }
        if (this->layer == 3 && this->version != MP3_V1_0)                 /* 375 */
            this->frame_size =
                    1 * (this->padding + 72 * this->bit_rate / this->sample_rate);
        if (this->frame_size > MAX_FRAME)
            return 0;
        return 1;
    }


} ;

typedef struct mp3_options
{                                                                    /*  19 */
    int flags;                                                         /*  20 */
#define MP3_TWO_CHANNEL_MONO 0x0100                                  /*  21 */
    int (*info_callback) (mp3_info * p);                               /*  22 */
#define MP3_INFO_NEVER    0x00                                       /*  29 */
#define MP3_INFO_IGNORE   0x01
#define MP3_INFO_ONCE     0x02
#define MP3_INFO_FRAME    0x04
#define MP3_INFO_READ     0x08
#define MP3_INFO_PCM      0x10
#define MP3_INFO_MPG      0x20
#define MP3_INFO_CRC      0x40
#define MP3_INFO_RESERVED 0x80
#define MP3_SYNC_1         0x0400                                    /*  35 */
#define MP3_SYNC_2         0x0000
#define MP3_SYNC_3         0x0800
    void (*tag_handler) (int id, int tag_read (int id, void *buffer, int count)   /*  38 */
    );
#define MP3_DONT_FLUSH 0x0200                                        /*  73 */
#define MP3_NO_PARTIAL_FRAME 0x1000                                  /* 330 */
    unsigned char (*equalizer)[32];                                    /* 444 */
} mp3_options;

extern int mp3_open (std::ifstream*  ,
                     int (*input_read) (int id,std::ifstream*  , void *buffer, size_t )     /*  12 */
        , mp3_options *                  /*  18 */
);

extern int
mp3_read (int id, mp3_sample * buffer, int size)                     /*  13 */
;

extern int mp3_close (int id)                                   /*  16 */
;
