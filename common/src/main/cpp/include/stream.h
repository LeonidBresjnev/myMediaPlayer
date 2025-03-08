#include <fstream>
#include "tables.h"                                                  /* 128 */
#include "huffman.h"                                                 /* 304 */

#define WINDOWBLOCKS 16                                              /*   1 */
#define SHIFTBLOCKS (2*BLOCKS + WINDOWBLOCKS-1)
#define SHIFTSIZE (SHIFTBLOCKS*SUBBANDS)
#define CHANNELS 2


typedef short int mp3_sample;                                        /*  14 */
extern void dct32 (const double *y, double *v);                      /*   5 */

extern void windowing (const double *v, mp3_sample * x);

#define STREAMS 512                                                  /*  41 */
#define END_OF_OUTPUT           0x0001                               /*  59 */
#define END_OF_INPUT  0x0002
#define LAYER_I   1                                                  /*  64 */
#define LAYER_II  2
#define LAYER_III 3
#define SUBBANDS 32                                                  /*  68 */
#define SUBFREQUENCIES 18
#define BLOCKS SUBFREQUENCIES
#define HEADER_SIZE 4                                                /*  77 */
#define BITALLOCATION_ERROR 0x0008                                   /* 109 */
#define OUTPUT_EXPONENT (sizeof(int)*8-1)                            /* 137 */
#define BUFFERSIZE (2*MAX_FRAME+HEADER_SIZE+MAX_RESERVOIR)           /* 148 */

//*static int tag_read (int id, void *buffer, int count)                           /*  38 */;

#define GROUPS 3                                                     /* 202 */
static unsigned short int
bitcrc (unsigned short int crc, unsigned short int bits, int n)
{                                                                    /* 438 */
    bits = bits << (16 - n);
    while (n-- > 0) {
        unsigned short int msb = (bits ^ crc) & 0x8000;

        crc = crc << 1;
        bits = bits << 1;
        if (msb)
            crc = crc ^ 0x8005;
    }
    return crc;
}

extern void dct18 (const double *z, double *t);                 /* 219 */

#define LONG_BLOCK  0                                                /* 225 */
#define START_BLOCK 1
#define SHORT_BLOCK 2
#define STOP_BLOCK  3
extern void dct6 (const double *z, double *t);                  /* 227 */

#define FREQUENCIES 576                                              /* 235 */
#define utothreequarter(ch,i) ((uv[ch][i]>=0)? power43[uv[ch][i]]:-power43[-uv[ch][i]]) /* 239 */
#define POWER43SIZE ((1<<13)+15)                                     /* 240 */
#define BANDS 39                                                     /* 242 */
#define GRANULES 2                                                   /* 247 */
#define twotomquarter(m)  power14[m+POWER14START]                    /* 250 */
#define SUBBLOCKS 3                                                  /* 264 */
#define POWER14START (326 - 4*OUTPUT_EXPONENT)                       /* 266 */
#define POWER14SIZE  (326 + 1 + 45)                                  /* 267 */
#define STEREO    0                                                  /* 280 */
#define INTENSITY 1
#define MID_SIDE  2
#define NONE 3                                                       /* 286 */
#define HWIDTH 6                                                     /* 298 */
#define HUFFMAN_CACHE_SIZE (sizeof(int)*8)                           /* 312 */
#define MAX_RESERVOIR 512                                            /* 321 */
#define BANDGROUPS 4                                                 /* 334 */
#define REGIONS 3                                                    /* 350 */
#define INTENSITY_V2 3                                               /* 387 */
#define LAYER_III_V2 4                                               /* 396 */
#define MUTE     5                                                   /* 400 */
#define REPEAT   6
#define REPAIR   7
#define SKIP     8
#ifndef max                                                          /* 437 */
#define max(a,b) ((a) >  (b) ? (a) : (b))
#endif
#ifndef min
#define min(a,b) ((a) >  (b) ? (b) : (a))
#endif
#ifndef M_PI
#define M_PI 3.14159265358979323846264338328
#endif
#define twotom32th(m)  power132[m]                                   /* 449 */

#define MAX_FRAME 1729                                               /* 170 */

static double y[BLOCKS][CHANNELS][SUBBANDS];                         /*  67 */

static const short int *width[GRANULES][CHANNELS];                   /* 244 */

static unsigned char global_gain[GRANULES][CHANNELS];                /* 246 */

static const int *preemphasis[GRANULES][CHANNELS];                   /* 258 */


static int scale_shift[GRANULES][CHANNELS];                          /* 253 */


static short int uv[CHANNELS][FREQUENCIES] = { {0} };                /* 236 */

static double z[CHANNELS][FREQUENCIES];                              /* 241 */


static char subblock_gain[GRANULES][CHANNELS][SUBBLOCKS];            /* 263 */

//static int decode_header (, unsigned char *);

static void
qs_band (const int ch, int i, int j, int width, unsigned int m, int step)
{                                                                    /* 243 */
    const double f = twotomquarter (m);

    while (width-- > 0) {
        z[ch][j] = utothreequarter (ch, i) * f;
        i = i + 1;
        j = j + step;
    }
}

static void
qs_intensity_band (int i, int j, int width, int m, int sp, int step)
{                                                                    /* 273 */
    double fL, fR;

    const double p = intensity_factor[sp];                             /* 277 */

    {
        double f = twotomquarter (m);                                    /* 271 */

        fL = f * (1 - p);
        fR = f * p;
    }
    while (width-- > 0) { {
            double u = utothreequarter (0, i);                             /* 272 */

            z[0][j] = u * fL;
            z[1][j] = u * fR;
        }
        i = i + 1;
        j = j + step;
    }
}


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
    unsigned int layer;
    int crc_protected;                                                 /*  32 */
    int bit_rate;
    unsigned int frame_size;
    int frame_position;
    int samples;
    unsigned int private_;
    unsigned int mode;
#define MP3_STEREO       0x00
#define MP3_JOINT_STEREO 0x01
#define MP3_DUAL_CHANNEL 0x02
#define MP3_MONO         0x03
    unsigned int copyright;
    unsigned int original;
    unsigned int emphasis;
    int frame;
    int sample_rate;                                                   /*  33 */
    int channels;
    int bit_per_sample;
    int free_format;                                                   /*  84 */
    unsigned int frequency_index;                                               /*  86 */
    unsigned int padding;                                                       /*  89 */
    unsigned int bound;                                                         /*  96 */
    int changes;                                                       /* 182 */
    unsigned int ms_stereo;                                                     /* 282 */
    unsigned int i_stereo;
    int fixed_size;                                                    /* 431 */



    int decode_header ( unsigned char *frame)
    {                                                                    /*  78 */
        unsigned int header;

        header = (((((frame[0] << 8) | frame[1]) << 8) | frame[2]) << 8) | frame[3];
        this->header = header;
        {
            int n = 11;                                                      /*  79 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits != 0x7FF)
                return 0;
        }
        {
            int n = 2;                                                       /*  81 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

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

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits == 0)
                return 0;
            this->layer = 4 - bits;
        }
        {
            int n = 1;                                                       /*  83 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->crc_protected = (bits == 0);
        }
        {
            int n = 4;                                                       /*  85 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

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

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            if (bits == 3)
                return 0;
            this->frequency_index = bits;
            this->sample_rate = frequency_table[this->version][this->frequency_index];
        }
        {
            int n = 1;                                                       /*  88 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->padding = bits;
        }
        {
            int n = 1;                                                       /*  92 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->private_ = bits;
        }
        {
            int n = 2;                                                       /*  94 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->mode = bits;
            if (this->mode == MP3_MONO)
                this->channels = 1;
            else
                this->channels = 2;
        }
        {
            int n = 2;                                                       /*  95 */

            unsigned int bits = header >> (32 - n);                                   /*  80 */

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

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->copyright = bits;
        }
        {
            int n = 1;

            unsigned int bits = header >> (32 - n);                                   /*  80 */

            header = header << n;
            this->original = bits;
        }
        {
            int n = 2;

            unsigned int bits = header >> (32 - n);                                   /*  80 */

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

class stream;

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

typedef struct
{
    char bit_allocation;                                          /* 105 */
    double mfactor[GROUPS];                                       /* 110 */
    char scfi;                                                    /* 204 */
} side_information;


static side_information side_info[SUBBANDS][CHANNELS];               /* 102 */

class stream {
public:

    double w[CHANNELS][SHIFTSIZE];                                /*   2 */
    int offset[CHANNELS];                                         /*   3 */
    mp3_info info;                                                /*  24 */
    int (*input_read) (int id,std::shared_ptr<std::ifstream>, void *buffer, size_t size)         /*  12 */
    ;
    mp3_options options;
    int state;                                                    /*  58 */
    char bit_offset;                                              /* 145 */
    unsigned char *byte_pointer;
    unsigned char buffer[BUFFERSIZE];                             /* 149 */
    unsigned char *start;                                         /* 151 */
    unsigned char *finish;
    int buffer_position;                                          /* 153 */
    unsigned char *frame;                                         /* 154 */
    int tag_size;                                                 /* 173 */
    unsigned int previous_header;                                 /* 185 */
    int sblimit[CHANNELS];                                        /* 193 */
    const signed char *nbal;                                             /* 196 */
    const signed char (*nbit)[16];                                       /* 197 */
    double tprime[CHANNELS][SUBBANDS][SUBFREQUENCIES / 2];        /* 220 */
    int block_type[GRANULES][CHANNELS];                           /* 226 */
    int mixed_block[GRANULES][CHANNELS];                          /* 233 */
    unsigned char *main_data_start;                               /* 324 */
    int share[CHANNELS][BANDGROUPS];                              /* 336 */
    const signed char *slength[GRANULES][CHANNELS];                      /* 346 */
    const char *slimit[GRANULES][CHANNELS];
    int bigtable[GRANULES][CHANNELS][REGIONS];                    /* 352 */
    int bigpairs[GRANULES][CHANNELS][REGIONS];                    /* 353 */
    int smalltable_A[GRANULES][CHANNELS];                         /* 361 */
    char sfi[CHANNELS][BANDS];                                    /* 366 */
    char sfimax[CHANNELS][BANDS];
    std::shared_ptr<std::ifstream> myFile;

    ~stream() = default;

    void  output_silence (mp3_sample * , int );


    void    output_repeat ( mp3_sample * , int , int );

    short int *  decode_small_A ( short int *, int , int ) const;

    short int * decode_small_B ( short int *, int , int ) const;

    void decode_big ( short int *, int , int );

    void decode_very_big ( short int *, int , int );

    void output_blocks ( mp3_sample * buffer, const int n                /*  69 */
    ) {

        for (int i = 0; i < n; i++) {
            double *v;                                                       /*  70 */

            {
                int ch = 0;

                this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
                if (this->offset[ch] < 0) {
                    this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                    memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                             &(this->w[ch][0]),
                             sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
                }
                v = this->w[ch] + this->offset[ch];
                if (this->options.equalizer != nullptr) {

                    for (int sb = 0; sb < SUBBANDS; sb++) {
                        int m = this->options.equalizer[ch][sb];

                        if (m == 0)
                            y[i][ch][sb] = 0.0;
                        else
                            y[i][ch][sb] *= twotom32th (m);
                    }
                }
                dct32 (y[i][ch], v);
                windowing (v, buffer);
            }
            if (this->info.channels > 1) {
                int ch = 1;

                this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
                if (this->offset[ch] < 0) {
                    this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                    memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                             &(this->w[ch][0]),
                             sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
                }
                v = this->w[ch] + this->offset[ch];
                if (this->options.equalizer != nullptr) {

                    for (int sb = 0; sb < SUBBANDS; sb++) {
                        int m = this->options.equalizer[ch][sb];

                        if (m == 0)
                            y[i][ch][sb] = 0.0;
                        else
                            y[i][ch][sb] *= twotom32th (m);
                    }
                }
                dct32 (y[i][ch], v);
                windowing (v, buffer + 1);
                buffer = buffer + 2 * SUBBANDS;
            }
            else {
                int sb;                                                        /*  71 */

                if (this->options.flags & MP3_TWO_CHANNEL_MONO) {
                    for (sb = 0; sb < SUBBANDS; sb++)
                        buffer[2 * sb + 1] = buffer[2 * sb];
                    buffer = buffer + 2 * SUBBANDS;
                }
                else {
                    for (sb = 0; sb < SUBBANDS; sb++)
                        buffer[sb] = buffer[2 * sb];
                    buffer = buffer + SUBBANDS;
                }
            }
        }
        if (this->options.flags & MP3_TWO_CHANNEL_MONO)
            this->info.samples += n * 2 * SUBBANDS;
        else
            this->info.samples += n * this->info.channels * SUBBANDS;
    }


    int qs ( const int gr, const int ch, int band, int i, const int limit)
    {                                                                    /* 268 */
        while (i < limit) {
            qs_band (ch, i, i, width[gr][ch][band], (4 * OUTPUT_EXPONENT     /* 262 */
                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                     +(-((this->sfi[ch][band]   /* 252 */
                                                          +preemphasis[gr][ch][band])
                             << scale_shift[gr][ch] /* 256 */
                     ))
                     )
                    , 1);
            i = i + width[gr][ch][band];
            band++;
        }
        return band;
    }

    int qs_short ( const int gr, const int ch, int band, int i,
               const int limit)
    {                                                                    /* 269 */
        while (i < limit) {
            int j = i, size = width[gr][ch][band];

            for (int k = 0; k < SUBBLOCKS; k++, j++, i = i + size, band++)
                qs_band (ch, i, j, size, (4 * OUTPUT_EXPONENT                  /* 265 */
                                          + (global_gain[gr][ch] - 210)        /* 251 */
                                          -8 * subblock_gain[gr][ch][k]
                                          - ((this->sfi[ch][band]                 /* 252 */
                         ) << scale_shift[gr][ch]          /* 256 */
                                          )
                         )
                        , 3);
        }
        return band;
    }

    unsigned char * next_frame ();


    void layer_II_decode_samples ( int );

    void  fill_input_buffer ();

    short unsigned int  crc_check ();

    void  layer_I_decode_samples ();

    unsigned int  getbit (int );

    unsigned char * synchronize (int (*) (int , void *, int ));

} ;


