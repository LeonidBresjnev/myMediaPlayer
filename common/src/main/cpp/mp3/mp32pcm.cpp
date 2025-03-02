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
#include "mp32pcm.h"                                                 /*   9 */
#include <cstring>                                                  /*  49 */
#include "tables.h"                                                  /* 128 */
#include "huffman.h"                                                 /* 304 */
#define WINDOWBLOCKS 16                                              /*   1 */
#define SHIFTBLOCKS (2*BLOCKS + WINDOWBLOCKS-1)
#define SHIFTSIZE (SHIFTBLOCKS*SUBBANDS)
#define CHANNELS 2
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
#define MAX_FRAME 1729                                               /* 170 */
static int
tag_read (int id, void *buffer, int count)                           /*  38 */
;

#define GROUPS 3                                                     /* 202 */
static unsigned short int                                       /* 211 */
bitcrc (unsigned short int crc, unsigned short int bits, int n);

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


static double y[BLOCKS][CHANNELS][SUBBANDS];                         /*  67 */

static const short int *width[GRANULES][CHANNELS];                   /* 244 */

static unsigned char global_gain[GRANULES][CHANNELS];                /* 246 */

static const int *preemphasis[GRANULES][CHANNELS];                   /* 258 */


static int scale_shift[GRANULES][CHANNELS];                          /* 253 */


static short int uv[CHANNELS][FREQUENCIES] = { {0} };                /* 236 */

static double z[CHANNELS][FREQUENCIES];                              /* 241 */


static char subblock_gain[GRANULES][CHANNELS][SUBBLOCKS];            /* 263 */

static int decode_header (mp3_info * , unsigned char *);

static void
qs_band (const int ch, int i, int j, int width, int m, int step)
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
    int (*input_read) (int id,std::ifstream*, void *buffer, size_t size)         /*  12 */
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
    std::ifstream *myFile;

    void  output_silence (mp3_sample * buffer, int n)
    {                                                                    /*  74 */
        int i;

        for (i = 0; i < n; i++) {
            double *v;                                                       /*  75 */

            {
                int sb, ch = 0;

                this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
                if (this->offset[ch] < 0) {
                    this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                    memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                             &(this->w[ch][0]),
                             sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
                }
                v = this->w[ch] + this->offset[ch];
                for (sb = SUBBANDS - 1; sb >= 0; sb--)
                    v[sb] = 0.0;
                windowing (v, buffer);
            }
            if (this->info.channels > 1) {
                int sb, ch = 1;

                this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
                if (this->offset[ch] < 0) {
                    this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                    memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                             &(this->w[ch][0]),
                             sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
                }
                v = this->w[ch] + this->offset[ch];
                for (sb = SUBBANDS - 1; sb >= 0; sb--)
                    v[sb] = 0.0;
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


    void    output_repeat ( mp3_sample * buffer, int n, int d)
    {                                                                    /* 406 */
        int i;

        for (i = 0; i < n; i++) {
            int ch;                                                          /* 408 */

            double *v;

            {
                ch = 0;
                this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
                if (this->offset[ch] < 0) {
                    this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                    memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                             &(this->w[ch][0]),
                             sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
                }
                v = this->w[ch] + this->offset[ch];
                {
                    int previous_offset;                                         /* 407 */

                    previous_offset = this->offset[ch] + d * SUBBANDS;
                    if (previous_offset >= SHIFTSIZE)
                        previous_offset =
                                previous_offset - SHIFTSIZE + (WINDOWBLOCKS - 1) * SUBBANDS;
                    memmove (v, this->w[ch] + previous_offset, sizeof (double) * SUBBANDS);
                }
                windowing (v, buffer);
            }
            if (this->info.channels > 1) {
                ch = 1;
                this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
                if (this->offset[ch] < 0) {
                    this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                    memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                             &(this->w[ch][0]),
                             sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
                }
                v = this->w[ch] + this->offset[ch];
                {
                    int previous_offset;                                         /* 407 */

                    previous_offset = this->offset[ch] + d * SUBBANDS;
                    if (previous_offset >= SHIFTSIZE)
                        previous_offset =
                                previous_offset - SHIFTSIZE + (WINDOWBLOCKS - 1) * SUBBANDS;
                    memmove (v, this->w[ch] + previous_offset, sizeof (double) * SUBBANDS);
                }
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

    short int *
    decode_small_A ( short int *u, int bits_available, int n) const
    {                                                                    /* 302 */
        unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

        int huffman_cache_size = 8 - this->bit_offset;

        int huffman_cache =
                (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
        bits_available = bits_available - huffman_cache_size;
        while (n >= 4) {
            unsigned char code;

            if ((16 - huffman_cache_size > 0)                                /* 315 */
                    ) {
                {
                    int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF);  /* 316 */

                    byte_pointer = byte_pointer + 2;
                    huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                    huffman_cache_size = huffman_cache_size + 16;
                }
                bits_available = bits_available - 16;                          /* 318 */
                if (bits_available < 0) {
                    huffman_cache_size = huffman_cache_size + bits_available;
                    bits_available = 0;
                    if (huffman_cache_size <= 0)
                        break;
                }
            }
            code = htabA[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 6)];   /* 299 */
            huffman_cache = huffman_cache << (code & 0xF);
            huffman_cache_size = huffman_cache_size - (code & 0xF);
            code = (code & 0xF0) | (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 4));        /* 300 */
            *u++ = signed_small_values[code].u0;
            *u++ = signed_small_values[code].u1;
            *u++ = signed_small_values[code].u2;
            *u++ = signed_small_values[code].u3;
            huffman_cache = huffman_cache << (signed_small_values[code].n);
            huffman_cache_size = huffman_cache_size - (signed_small_values[code].n);
            n = n - 4;
        }
        return u;
    }

    short int *
    decode_small_B ( short int *u, int bits_available, int n) const
    {                                                                    /* 303 */
        unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

        int huffman_cache_size = 8 - this->bit_offset;

        int huffman_cache =
                (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
        bits_available = bits_available - huffman_cache_size;
        while (n >= 4) {
            unsigned char code;

            if ((16 - huffman_cache_size > 0)                                /* 315 */
                    ) {
                {
                    int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF);  /* 316 */

                    byte_pointer = byte_pointer + 2;
                    huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                    huffman_cache_size = huffman_cache_size + 16;
                }
                bits_available = bits_available - 16;                          /* 318 */
                if (bits_available < 0) {
                    huffman_cache_size = huffman_cache_size + bits_available;
                    bits_available = 0;
                    if (huffman_cache_size <= 0)
                        break;
                }
            }
            code = ~(huffman_cache >> (HUFFMAN_CACHE_SIZE - 4));             /* 301 */
            huffman_cache = huffman_cache << 4;
            huffman_cache_size = huffman_cache_size - 4;
            code = code << 4;
            code = (code & 0xF0) | (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 4));        /* 300 */
            *u++ = signed_small_values[code].u0;
            *u++ = signed_small_values[code].u1;
            *u++ = signed_small_values[code].u2;
            *u++ = signed_small_values[code].u3;
            huffman_cache = huffman_cache << (signed_small_values[code].n);
            huffman_cache_size = huffman_cache_size - (signed_small_values[code].n);
            n = n - 4;
        }
        return u;
    }

    void
    decode_big ( short int *u, int k, int n)
    {                                                                    /* 310 */
        short int *htab = huffman_tables[k].h;

        unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

        int huffman_cache_size = 8 - this->bit_offset;

        int huffman_cache =
                (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
        while (n-- > 0) {
            int code;

            if ((16 - huffman_cache_size > 0)                                /* 315 */
                    ) {
                int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

                byte_pointer = byte_pointer + 2;
                huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                huffman_cache_size = huffman_cache_size + 16;
            }
            {
                int width = HWIDTH;                                            /* 305 */

                short int *h = htab;

                code = h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
                while (code < 0) {
                    huffman_cache = huffman_cache << width;
                    huffman_cache_size = huffman_cache_size - width;
                    h = h - (code >> 4);
                    width = code & 0x0F;
                    code =
                            h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
                }
            }
            huffman_cache = huffman_cache << (code >> 8);                    /* 306 */
            huffman_cache_size = huffman_cache_size - (code >> 8);
            code = code & 0xFF;
            {
                short int value = code >> 4;                                   /* 307 */

                if (value != 0) {
                    int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                    value = (value ^ tmp) - tmp;
                    huffman_cache = huffman_cache << 1;
                    huffman_cache_size--;
                }
                *u++ = value;
            }
            code = (code << 4) & 0xFF;
            {
                short int value = code >> 4;                                   /* 307 */

                if (value != 0) {
                    int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                    value = (value ^ tmp) - tmp;
                    huffman_cache = huffman_cache << 1;
                    huffman_cache_size--;
                }
                *u++ = value;
            }
            code = (code << 4) & 0xFF;
        }
        this->bit_offset = (8 - (huffman_cache_size % 8)) & 0x7;              /* 314 */
        this->byte_pointer = byte_pointer - ((huffman_cache_size + this->bit_offset) / 8);
    }

    void
    decode_very_big ( short int *u, int k, int n)
    {                                                                    /* 311 */
        short int *htab = huffman_tables[k].h;

        int linbits = huffman_tables[k].linbits;

        unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

        int huffman_cache_size = 8 - this->bit_offset;

        int huffman_cache =
                (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
        while (n-- > 0) {
            int code;

            if ((16 - huffman_cache_size > 0)                                /* 315 */
                    ) {
                int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

                byte_pointer = byte_pointer + 2;
                huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                huffman_cache_size = huffman_cache_size + 16;
            }
            if (huffman_cache_size < 24) {                                   /* 317 */
                huffman_cache =
                        huffman_cache | (byte_pointer[0] << (24 - huffman_cache_size));
                byte_pointer++;
                huffman_cache_size = huffman_cache_size + 8;
            }
            {
                int width = HWIDTH;                                            /* 305 */

                short int *h = htab;

                code = h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
                while (code < 0) {
                    huffman_cache = huffman_cache << width;
                    huffman_cache_size = huffman_cache_size - width;
                    h = h - (code >> 4);
                    width = code & 0x0F;
                    code =
                            h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
                }
            }
            huffman_cache = huffman_cache << (code >> 8);                    /* 306 */
            huffman_cache_size = huffman_cache_size - (code >> 8);
            code = code & 0xFF;
            if ((16 - huffman_cache_size > 0)                                /* 315 */
                    ) {
                int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

                byte_pointer = byte_pointer + 2;
                huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                huffman_cache_size = huffman_cache_size + 16;
            }
            {
                short int value = code >> 4;                                   /* 309 */

                if (value == 15 && linbits > 0) {
                    value =
                            value +
                            (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - linbits));
                    huffman_cache = huffman_cache << linbits;
                    huffman_cache_size = huffman_cache_size - linbits;
                    {
                        int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);   /* 308 */

                        value = (value ^ tmp) - tmp;
                        huffman_cache = huffman_cache << 1;
                        huffman_cache_size--;
                    }
                }
                else if (value != 0) {
                    int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                    value = (value ^ tmp) - tmp;
                    huffman_cache = huffman_cache << 1;
                    huffman_cache_size--;
                }
                *u++ = value;
                code = (code << 4) & 0xFF;
            }
            if ((16 - huffman_cache_size > 0)                                /* 315 */
                    ) {
                int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

                byte_pointer = byte_pointer + 2;
                huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                huffman_cache_size = huffman_cache_size + 16;
            }
            {
                short int value = code >> 4;                                   /* 309 */

                if (value == 15 && linbits > 0) {
                    value =
                            value +
                            (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - linbits));
                    huffman_cache = huffman_cache << linbits;
                    huffman_cache_size = huffman_cache_size - linbits;
                    {
                        int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);   /* 308 */

                        value = (value ^ tmp) - tmp;
                        huffman_cache = huffman_cache << 1;
                        huffman_cache_size--;
                    }
                }
                else if (value != 0) {
                    int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                    value = (value ^ tmp) - tmp;
                    huffman_cache = huffman_cache << 1;
                    huffman_cache_size--;
                }
                *u++ = value;
                code = (code << 4) & 0xFF;
            }
        }
        this->bit_offset = (8 - (huffman_cache_size % 8)) & 0x7;              /* 314 */
        this->byte_pointer = byte_pointer - ((huffman_cache_size + this->bit_offset) / 8);
    }

    void output_blocks ( mp3_sample * buffer, int n                /*  69 */
    )
    {
        int i;

        for (i = 0; i < n; i++) {
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
                if (this->options.equalizer != nullptr) {                            /* 445 */
                    int sb;

                    for (sb = 0; sb < SUBBANDS; sb++) {
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
                if (this->options.equalizer != nullptr) {                            /* 445 */
                    int sb;

                    for (sb = 0; sb < SUBBANDS; sb++) {
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

    int
    qs ( const int gr, const int ch, int band, int i, const int limit)
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

    int
    qs_short ( const int gr, const int ch, int band, int i,
              const int limit)
    {                                                                    /* 269 */
        while (i < limit) {
            int k, j = i;

            int size = width[gr][ch][band];

            for (k = 0; k < SUBBLOCKS; k++, j++, i = i + size, band++)
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

    unsigned char *
    next_frame ()
    {                                                                    /* 166 */
        int previous_free_format;

        this->frame = this->frame + this->info.frame_size;
        this->info.frame++;
        while (this->frame + HEADER_SIZE > this->finish)
            if (this->state & END_OF_INPUT)
                return nullptr;
            else
                fill_input_buffer ();
        previous_free_format = this->info.free_format;
        if (!decode_header (&this->info, this->frame))
            return nullptr;
        this->byte_pointer = this->frame;
        this->bit_offset = 0;
        if (this->info.free_format && !previous_free_format)
            return nullptr;
        if (this->info.layer != 3)                                            /* 159 */
            this->start = this->frame;
        else if (this->frame - this->start > MAX_RESERVOIR)                      /* 160 */
            this->start = this->frame - MAX_RESERVOIR;
        while (this->frame + this->info.frame_size > this->finish)                  /* 162 */
            if (this->state & END_OF_INPUT) {
                if (this->info.layer == 3 && (this->frame + this->info.fixed_size < this->finish &&   /* 331 */
                                           !(this->options.flags & MP3_NO_PARTIAL_FRAME))
                        )
                    return this->frame;
                else
                    return nullptr;
            }
            else
                fill_input_buffer ();
        return this->frame;
    }


    void
    layer_II_decode_samples ( int g)
    {                                                                    /* 217 */
        char bit_offset = this->bit_offset;                          /* 146 */

        unsigned char *byte_pointer = this->byte_pointer;

        {
            int i;                                                           /* 218 */

            for (i = 0; i < 12; i = i + 3) {
                int sb, ch;

                for (sb = 0; sb < this->info.bound; sb++)
                    for (ch = 0; ch < this->info.channels; ch++) {
                        double f = side_info[sb][ch].mfactor[g];

                        int n = side_info[sb][ch].bit_allocation;

                        {
                            int sample;                                              /* 201 */

                            if (n > 0) {
                                {
                                    unsigned int bits;                                   /* 125 */

                                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                    bits |=
                                            ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                    bits |=
                                            ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                    bits <<= bit_offset;                                 /* 143 */
                                    bit_offset += n;
                                    byte_pointer += (bit_offset >> 3);
                                    bit_offset = bit_offset & 0x07;
                                    bits = bits ^ (((unsigned int) -1) >> 1)             /* 123 */
                                            ;
                                    bits = bits & ~(((unsigned int) -1) >> n)            /* 124 */
                                            ;
                                    sample = (int) bits;
                                }
                                y[i][ch][sb] = sample * f;
                                {
                                    unsigned int bits;                                   /* 125 */

                                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                    bits |=
                                            ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                    bits |=
                                            ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                    bits <<= bit_offset;                                 /* 143 */
                                    bit_offset += n;
                                    byte_pointer += (bit_offset >> 3);
                                    bit_offset = bit_offset & 0x07;
                                    bits = bits ^ (((unsigned int) -1) >> 1)             /* 123 */
                                            ;
                                    bits = bits & ~(((unsigned int) -1) >> n)            /* 124 */
                                            ;
                                    sample = (int) bits;
                                }
                                y[i + 1][ch][sb] = sample * f;
                                {
                                    unsigned int bits;                                   /* 125 */

                                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                    bits |=
                                            ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                    bits |=
                                            ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                    bits <<= bit_offset;                                 /* 143 */
                                    bit_offset += n;
                                    byte_pointer += (bit_offset >> 3);
                                    bit_offset = bit_offset & 0x07;
                                    bits = bits ^ (((unsigned int) -1) >> 1)             /* 123 */
                                            ;
                                    bits = bits & ~(((unsigned int) -1) >> n)            /* 124 */
                                            ;
                                    sample = (int) bits;
                                }
                                y[i + 2][ch][sb] = sample * f;
                            }
                            else if (n < 0) {                                        /* 424 */
                                int c;

                                {
                                    unsigned int bits;

                                    n = -n;
                                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                    bits |=
                                            ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                    bits |=
                                            ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                    bits <<= bit_offset;                                 /* 143 */
                                    bit_offset += n;
                                    byte_pointer += (bit_offset >> 3);
                                    bit_offset = bit_offset & 0x07;
                                    bits = (bits >> (sizeof (bits) * 8 - n));
                                    n = -n;
                                    c = bits;
                                }
                                {
                                    const int (*table)[3];                               /* 425 */

                                    table = degroup[-n - 5];
                                    y[i][ch][sb] = table[c][0] * f;                      /* 426 */
                                    y[i + 1][ch][sb] = table[c][1] * f;
                                    y[i + 2][ch][sb] = table[c][2] * f;
                                }}
                            else
                                y[i][ch][sb] = y[i + 1][ch][sb] = y[i + 2][ch][sb] = 0.0;
                        }
                    }
                for (sb = this->info.bound; sb < this->sblimit[0]; sb++) {
                    const double f = side_info[sb][0].mfactor[g];

                    const double r = side_info[sb][1].mfactor[g] / f;

                    int n = side_info[sb][0].bit_allocation;

                    ch = 0;
                    {
                        int sample;                                                /* 201 */

                        if (n > 0) {
                            {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i][ch][sb] = sample * f;
                            {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i + 1][ch][sb] = sample * f;
                            {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i + 2][ch][sb] = sample * f;
                        }
                        else if (n < 0) {                                          /* 424 */
                            int c;

                            {
                                unsigned int bits;

                                n = -n;
                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = (bits >> (sizeof (bits) * 8 - n));
                                n = -n;
                                c = bits;
                            }
                            {
                                const int (*table)[3];                                 /* 425 */

                                table = degroup[-n - 5];
                                y[i][ch][sb] = table[c][0] * f;                        /* 426 */
                                y[i + 1][ch][sb] = table[c][1] * f;
                                y[i + 2][ch][sb] = table[c][2] * f;
                            }}
                        else
                            y[i][ch][sb] = y[i + 1][ch][sb] = y[i + 2][ch][sb] = 0.0;
                    }
                    if (this->info.channels > 1) {
                        y[i][1][sb] = y[i][0][sb] * r;
                        y[i + 1][1][sb] = y[i + 1][0][sb] * r;
                        y[i + 2][1][sb] = y[i + 2][0][sb] * r;
                    }
                }
                for (sb = this->sblimit[0]; sb < SUBBANDS; sb++) {
                    y[i][0][sb] = y[i + 1][0][sb]
                            = y[i + 2][0][sb] = 0.0;
                    if (this->info.channels > 1)
                        y[i][1][sb] = y[i + 1][1][sb]
                                = y[i + 2][1][sb] = 0.0;
                }
            }
        }
        this->bit_offset = bit_offset;                                        /* 147 */
        this->byte_pointer = byte_pointer;
    }



    void
    fill_input_buffer ()
    {                                                                    /* 157 */
        int size = this->finish - this->start;

        if (size > 0) {                                                    /* 158 */
            int distance = this->start - this->buffer;

            if (distance > 0) {
                memmove (this->buffer, this->start, size);
                this->start = this->buffer;
                if (this->frame != nullptr)
                    this->frame = this->frame - distance;
                this->byte_pointer = this->byte_pointer - distance;
                this->finish = this->finish - distance;
                this->buffer_position = this->buffer_position + distance;
            }
        }
        size = this->input_read (this->info.id, this->myFile, this->finish, BUFFERSIZE - size);
        if (size <= 0)
            this->state = this->state | END_OF_INPUT;
        else
            this->finish = this->finish + size;
    }

    short unsigned int
    crc_check ()
    {                                                                    /* 101 */
        short unsigned int crc;

        unsigned char *byte_pointer;

        int n;

        crc = 0xFFFF;
        byte_pointer = this->frame + 2;                                       /* 100 */
        crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]         /* 441 */
                ;
        crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]         /* 441 */
                ;
        byte_pointer++;
        byte_pointer++;
        if (this->info.layer == 1) {
            n = 16;
            if (this->info.channels > 1)
                n = n + this->info.bound / 2;
            while (n-- > 0)
                crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]     /* 441 */
                        ;
        }
        else if (this->info.layer == 2) {
            char bit_offset = 0;                                             /* 215 */

            unsigned int bits;

            int sb, ch, nsf;

            nsf = 0;                                                         /* 213 */
            for (sb = 0; sb < this->info.bound; sb++)
                for (ch = 0; ch < this->info.channels; ch++) {
                    n = this->nbal[sb];
                    if (n > 0) {
                        bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);   /* 142 */
                        bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                        bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                        bits <<= bit_offset;                                       /* 143 */
                        bit_offset += n;
                        byte_pointer += (bit_offset >> 3);
                        bit_offset = bit_offset & 0x07;
                        bits = (bits >> (sizeof (bits) * 8 - n));
                        crc = bitcrc (crc, (unsigned short int) bits, n);
                        if (bits != 0)
                            nsf++;
                    }
                }
            for (; sb < this->sblimit[0]; sb++) {
                n = this->nbal[sb];
                if (n > 0) {
                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                    bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                    bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                    bits <<= bit_offset;                                         /* 143 */
                    bit_offset += n;
                    byte_pointer += (bit_offset >> 3);
                    bit_offset = bit_offset & 0x07;
                    bits = (bits >> (sizeof (bits) * 8 - n));
                    crc = bitcrc (crc, (unsigned short int) bits, n);
                    if (bits != 0)
                        nsf = nsf + this->info.channels;
                }
            }
            {
                int sfbit = nsf * 2;                                           /* 214 */

                if (bit_offset > 0) {
                    n = min (8 - bit_offset, sfbit);
                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                    bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                    bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                    bits <<= bit_offset;                                         /* 143 */
                    bit_offset += n;
                    byte_pointer += (bit_offset >> 3);
                    bit_offset = bit_offset & 0x07;
                    bits = (bits >> (sizeof (bits) * 8 - n));
                    crc = bitcrc (crc, (unsigned short int) bits, n);
                    sfbit = sfbit - n;
                }
                while (sfbit >= 8) {
                    crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]   /* 441 */
                            ;
                    sfbit = sfbit - 8;
                }
                if (sfbit > 0) {
                    n = sfbit;
                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                    bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                    bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                    bits <<= bit_offset;                                         /* 143 */
                    bit_offset += n;
                    byte_pointer += (bit_offset >> 3);
                    bit_offset = bit_offset & 0x07;
                    bits = (bits >> (sizeof (bits) * 8 - n));
                    crc = bitcrc (crc, (unsigned short int) bits, n);
                }
            }
        }
        else {
            n = this->info.fixed_size - HEADER_SIZE - 2;
            while (n-- > 0)
                crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]     /* 441 */
                        ;
        }
        return crc;
    }

    void
    layer_I_decode_samples ()
    {                                                                    /* 122 */
        char bit_offset = this->bit_offset;                          /* 146 */

        unsigned char *byte_pointer = this->byte_pointer;

        {
            int i;                                                           /* 116 */

            if (this->info.channels > 1)
                for (i = 0; i < 12; i++) {
                    int sb;                                                      /* 120 */

                    for (sb = 0; sb < this->info.bound; sb++) {
                        int sample;                                                /* 118 */

                        {
                            int n = side_info[sb][0].bit_allocation;

                            if (n == 0)                                              /* 121 */
                                sample = 0;
                            else {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i][0][sb] = sample * side_info[sb][0].mfactor[0];
                        }
                        {
                            int n = side_info[sb][1].bit_allocation;                 /* 119 */

                            if (n == 0)                                              /* 121 */
                                sample = 0;
                            else {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i][1][sb] = sample * side_info[sb][1].mfactor[0];
                        }
                    }
                    for (; sb < SUBBANDS; sb++) {
                        int sample;                                                /* 118 */

                        {
                            int n = side_info[sb][0].bit_allocation;

                            if (n == 0)                                              /* 121 */
                                sample = 0;
                            else {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i][0][sb] = sample * side_info[sb][0].mfactor[0];
                        }
                        y[i][1][sb] = sample * side_info[sb][1].mfactor[0];
                    }
                }
            else
                for (i = 0; i < 12; i++) {
                    int sb;                                                      /* 117 */

                    for (sb = 0; sb < SUBBANDS; sb++) {
                        int sample;                                                /* 118 */

                        {
                            int n = side_info[sb][0].bit_allocation;

                            if (n == 0)                                              /* 121 */
                                sample = 0;
                            else {
                                unsigned int bits;                                     /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                   /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i][0][sb] = sample * side_info[sb][0].mfactor[0];
                        }
                    }
                }
        }
        this->bit_offset = bit_offset;                                        /* 147 */
        this->byte_pointer = byte_pointer;
    }

    unsigned int
    getbit (const int n)
    {                                                                    /* 144 */
        unsigned int bits;

        char bit_offset = this->bit_offset;                          /* 146 */

        unsigned char *byte_pointer = this->byte_pointer;

        bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);   /* 142 */
        bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
        bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
        bits <<= bit_offset;                                               /* 143 */
        bit_offset += n;
        byte_pointer += (bit_offset >> 3);
        bit_offset = bit_offset & 0x07;
        bits = (bits >> (sizeof (bits) * 8 - n));
        this->bit_offset = bit_offset;                                        /* 147 */
        this->byte_pointer = byte_pointer;
        return bits;
    }



    unsigned char *
    synchronize ()
    {                                                                    /* 161 */
        if (this->bit_offset > 0) {
            this->byte_pointer++;
            this->bit_offset = 0;
        }
        do {
            while (this->byte_pointer + HEADER_SIZE > this->finish)
                if (this->state & END_OF_INPUT)
                    return NULL;
                else
                    this->fill_input_buffer ();
            if (decode_header (&this->info, this->byte_pointer)) {
                this->frame = this->byte_pointer;
                {
                    if (this->info.free_format) {                                   /* 164 */
                        int frame_size = HEADER_SIZE;                              /* 167 */

                        do {
                            if (++frame_size > MAX_FRAME)
                                break;
                            while (this->frame + frame_size + HEADER_SIZE > this->finish)  /* 169 */
                                if (this->state & END_OF_INPUT)
                                    return NULL;
                                else
                                    this->fill_input_buffer ();
                            if (!(this->frame[frame_size] == 0xFF &&                    /* 168 */
                                  (this->frame[frame_size + 1] & 0xFE) == (this->frame[1] & 0xFE) &&
                                  (this->frame[frame_size + 2] & 0xFC) == (this->frame[2] & 0xFC))
                                    )
                                continue;
                            {
                                mp3_info i = { 0 }
                                , *info = &i;                                          /* 171 */
                                if (!decode_header (info, this->frame + frame_size))
                                    continue;
                                this->info.frame_size = frame_size;
                                {
                                    if (this->info.layer == 1)                              /*  91 */
                                        this->info.bit_rate =
                                                ((this->info.frame_size / 4 -
                                                  this->info.padding) * this->info.sample_rate + 11) / 12;
                                    else
                                        this->info.bit_rate =
                                                ((this->info.frame_size -
                                                  this->info.padding) * this->info.sample_rate + 143) / 144;
                                }
                                if (this->info.layer == 3 && this->info.version != MP3_V1_0) /* 376 */
                                    this->info.bit_rate =
                                            ((this->info.frame_size -
                                              this->info.padding) * this->info.sample_rate + 71) / 72;
                                if (this->options.flags & MP3_SYNC_3) {
                                    info->bit_rate = this->info.bit_rate;
                                    {
                                        if (info->layer == 1)                              /*  90 */
                                            info->frame_size =
                                                    4 * (info->padding +
                                                         (384 / (8 * 4)) * info->bit_rate /
                                                         info->sample_rate);
                                        else
                                            info->frame_size =
                                                    info->padding +
                                                    (1152 / 8) * info->bit_rate / info->sample_rate;
                                    }
                                    if (info->layer == 3 && info->version != MP3_V1_0)   /* 375 */
                                        info->frame_size =
                                                1 * (info->padding +
                                                     72 * info->bit_rate / info->sample_rate);
                                    while (this->frame + this->info.frame_size + info->frame_size +
                                           HEADER_SIZE > this->finish)
                                        if (this->state & END_OF_INPUT)
                                            return this->frame;
                                        else
                                            this->fill_input_buffer ();
                                    if (decode_header
                                            (info, this->frame + this->info.frame_size + info->frame_size))
                                        return this->frame;
                                    else
                                        break;
                                }
                                else
                                    return this->frame;
                            }
                        } while (1);
                    }
                    else {
                        while (this->frame + this->info.frame_size > this->finish)          /* 162 */
                            if (this->state & END_OF_INPUT) {
                                if (this->info.layer == 3 && (this->frame + this->info.fixed_size < this->finish &&   /* 331 */
                                                           !(this->options.
                                                                   flags & MP3_NO_PARTIAL_FRAME))
                                        )
                                    return this->frame;
                                else
                                    return NULL;
                            }
                            else
                                this->fill_input_buffer ();
                        if (this->options.flags & MP3_SYNC_1)
                            return this->frame;
                        else {
                            mp3_info i = { 0 };                                      /* 165 */
                            while (this->frame + this->info.frame_size + HEADER_SIZE > this->finish)
                                if (this->state & END_OF_INPUT)
                                    return this->frame;
                                else
                                    this->fill_input_buffer ();
                            if (decode_header (&i, this->frame + this->info.frame_size)) {
                                if (this->options.flags & MP3_SYNC_3) {
                                    while (this->frame + this->info.frame_size + i.frame_size +
                                           HEADER_SIZE > this->finish)
                                        if (this->state & END_OF_INPUT)
                                            return this->frame;
                                        else
                                            this->fill_input_buffer ();
                                    if (i.free_format)
                                        return this->frame;
                                    else
                                    if (decode_header
                                            (&i, this->frame + this->info.frame_size + i.frame_size))
                                        return this->frame;
                                }
                                else
                                    return this->frame;
                            }
                        }
                    }
                    this->frame = NULL;
                    this->byte_pointer++;
                }
            }
            else {
                if (this->options.tag_handler != NULL) {                          /* 172 */
                    this->tag_size = 0;                                             /* 174 */
                    this->options.tag_handler (this->info.id, tag_read);
                    if (this->tag_size <= 0)
                        this->byte_pointer++;
                    else {
                        int post = this->finish - this->byte_pointer - this->tag_size;      /* 179 */

                        int pre = this->byte_pointer - this->start;

                        if (post > 0) {
                            if (pre > 0)
                                memmove (this->start + this->tag_size, this->start, pre);
                            this->byte_pointer = this->byte_pointer + this->tag_size;
                            this->start = this->start + this->tag_size;
                            if (this->frame != NULL)
                                this->frame = this->frame + this->tag_size;
                        }
                        else
                            this->finish = this->byte_pointer;
                    }
                }
                else
                    this->byte_pointer++;
            }
            if (this->byte_pointer - this->start > MAX_RESERVOIR)
                this->start = this->byte_pointer - MAX_RESERVOIR;
        } while (1);
        return NULL;
    }


} ;



static stream *streams[STREAMS] = { nullptr };                     /*  43 */

static int output_mode;                                              /*  63 */

static const short int *start[GRANULES][CHANNELS];



static const int                                                     /* 257 */

preemphasis_table[BANDS] =
        { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 0 };
static const int zero_table[BANDS] = { 0 };


static int ulimit[CHANNELS];                                         /* 284 */

static int main_data_bit[GRANULES][CHANNELS];                        /* 340 */

static int big_values[GRANULES][CHANNELS];                           /* 342 */

static int window_switching[GRANULES][CHANNELS];                     /* 348 */

static const char slimit_v2[6][2][2][6] =                            /* 386 */
        { {{{6, 11, 16, 21, 22, 0}, {6, 11, 16, 21, 22, 0}},
                  {{9, 18, 27, 36, 39, 0}, {6, 15, 24, 33, 36, 0}}},
          {{{6, 11, 18, 21, 22, 0}, {6, 11, 18, 21, 22, 0}},
                  {{9, 18, 30, 36, 39, 0}, {6, 15, 27, 33, 36, 0}}},
          {{{11, 21, 21, 21, 22, 0}, {11, 21, 21, 21, 22, 0}},
                  {{18, 36, 36, 36, 39, 0}, {15, 33, 33, 33, 36, 0}}}
        };
static const char slimit_v2i[6][2][2][6] =
        { {{{7, 14, 21, 21, 22, 0}, {7, 14, 21, 21, 22, 0}},
                  {{12, 24, 36, 36, 39, 0}, {6, 21, 33, 33, 36, 0}}},
          {{{6, 12, 18, 21, 22, 0}, {6, 12, 18, 21, 22, 0}},
                  {{12, 21, 30, 36, 39, 0}, {6, 18, 27, 33, 36, 0}}},
          {{{8, 16, 21, 21, 22, 0}, {8, 16, 21, 21, 22, 0}},
                  {{15, 27, 36, 36, 39, 0}, {6, 24, 33, 33, 36, 0}}}
        };

static char i_scale;                                                 /* 389 */

static const int bit_rate_table[3][3][16] = {                        /* 415 */
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

static const int frequency_table[3][3] = {                           /* 416 */
        {44100, 48000, 32000},
        {22050, 24000, 16000},
        {11025, 12000, 8000}
};
static const int boundary_table[3] = { 8, 6, 6 };                    /* 427 */

#define FIRST_MIXED_SHORT 3
static const signed char slength_v1[16][4] =                                /* 435 */
        { {0, 0, 0, -1},
          {0, 1, 0, -1},
          {0, 2, 0, -1},
          {0, 3, 0, -1},
          {3, 0, 0, -1},
          {1, 1, 0, -1},
          {1, 2, 0, -1},
          {1, 3, 0, -1},
          {2, 1, 0, -1},
          {2, 2, 0, -1},
          {2, 3, 0, -1},
          {3, 1, 0, -1},
          {3, 2, 0, -1},
          {3, 3, 0, -1},
          {4, 2, 0, -1},
          {4, 3, 0, -1}
        };

static const char slimit_v1[2][2][4] =                               /* 436 */
        { {{11, 21, 22, 0}, {11, 21, 22, 0}}, {{18, 36, 39, 0}, {17, 35, 38, 0}} };





static int
decode_header (mp3_info * info, unsigned char *frame)
{                                                                    /*  78 */
    unsigned int header;

    header = (((((frame[0] << 8) | frame[1]) << 8) | frame[2]) << 8) | frame[3];
    info->header = header;
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
            info->version = MP3_V2_5;
        else if (bits == 2)
            info->version = MP3_V2_0;
        else if (bits == 3)
            info->version = MP3_V1_0;
        else
            return 0;
    }
    {
        int n = 2;                                                       /*  82 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        if (bits == 0)
            return 0;
        info->layer = 4 - bits;
    }
    {
        int n = 1;                                                       /*  83 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->crc_protected = (bits == 0);
    }
    {
        int n = 4;                                                       /*  85 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        if (bits == 0)
            info->free_format = 1;
        else if (bits == 0xF)
            return 0;
        else {
            info->free_format = 0;
            info->bit_rate = bit_rate_table[info->version][info->layer - 1][bits];
        }
    }
    {
        int n = 2;                                                       /*  87 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        if (bits == 3)
            return 0;
        info->frequency_index = bits;
        info->sample_rate = frequency_table[info->version][info->frequency_index];
    }
    {
        int n = 1;                                                       /*  88 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->padding = bits;
    }
    {
        int n = 1;                                                       /*  92 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->private_ = bits;
    }
    {
        int n = 2;                                                       /*  94 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->mode = bits;
        if (info->mode == MP3_MONO)
            info->channels = 1;
        else
            info->channels = 2;
    }
    {
        int n = 2;                                                       /*  95 */

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->bound = bits * 4 + 4;                                      /*  97 */
        if (info->mode != MP3_JOINT_STEREO)
            info->bound = 32;
        if (info->mode == MP3_JOINT_STEREO) {                            /* 281 */
            info->ms_stereo = (bits >> 1) & 1;
            info->i_stereo = bits & 1;
        }
        else
            info->ms_stereo = info->i_stereo = 0;
    }
    {
        int n = 1;

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->copyright = bits;
    }
    {
        int n = 1;

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->original = bits;
    }
    {
        int n = 2;

        int bits = header >> (32 - n);                                   /*  80 */

        header = header << n;
        info->emphasis = bits;
    }
    {
        if (info->layer == 1)                                            /*  90 */
            info->frame_size =
                    4 * (info->padding +
                         (384 / (8 * 4)) * info->bit_rate / info->sample_rate);
        else
            info->frame_size =
                    info->padding + (1152 / 8) * info->bit_rate / info->sample_rate;
    }
    if (info->layer == 3 && info->version != MP3_V1_0)                 /* 375 */
        info->frame_size =
                1 * (info->padding + 72 * info->bit_rate / info->sample_rate);
    if (info->frame_size > MAX_FRAME)
        return 0;
    return 1;
}


static int
tag_read (int id, void *buffer, int count)
{                                                                    /*  38 */
    stream *s;

    if (buffer == nullptr)
        return MP3_ERROR_NO_BUFFER;
    if (id >= STREAMS || id < 0)                                       /*  52 */
        return MP3_ERROR_NO_ID;
    s = streams[id];
    if (s == nullptr)
        return MP3_ERROR_NOT_OPEN;
    if (count > 0) {
        int available = s->finish - s->byte_pointer - s->tag_size;       /* 177 */

        if (available > 0) {
            if (count > available)
                count = available;
            memmove (buffer, s->byte_pointer + s->tag_size, count);
        }
        else
            count = s->input_read (s->info.id, s->myFile, buffer, count);
    }
    else if (count < 0) {
        int available = s->finish - s->byte_pointer - s->tag_size;       /* 178 */

        if (available > 0) {
            if (s->tag_size + count < 0)
                count = -s->tag_size;
            memmove (s->byte_pointer + s->tag_size + count, buffer, -count);
        }
        else {
            int size = s->byte_pointer - s->start;

            if (size > 0) {                                                /* 158 */
                int distance = s->start - s->buffer;

                if (distance > 0) {
                    memmove (s->buffer, s->start, size);
                    s->start = s->buffer;
                    if (s->frame != nullptr)
                        s->frame = s->frame - distance;
                    s->byte_pointer = s->byte_pointer - distance;
                    s->finish = s->finish - distance;
                    s->buffer_position = s->buffer_position + distance;
                }
            }
            if (s->tag_size > BUFFERSIZE - size) {
                s->buffer_position =
                        s->buffer_position + s->tag_size - (BUFFERSIZE - size);
                s->tag_size = BUFFERSIZE - size;
            }
            s->finish = s->byte_pointer + s->tag_size;
            memmove (s->byte_pointer + s->tag_size + count, buffer, -count);
        }
    }
    s->tag_size = s->tag_size + count;
    return count;
}


static void
qs_mid_side_band (int i, int j, int width, int mM, int mS, int step)
{                                                                    /* 270 */
    double fM = twotomquarter (mM - 2), fS = twotomquarter (mS - 2);

    while (width-- > 0) {
        double mid = utothreequarter (0, i) * fM,
                side = utothreequarter (1, i) * fS;
        z[0][j] = mid + side;
        z[1][j] = mid - side;
        i = i + 1;
        j = j + step;
    }
}




static void
qs_intensity_v2_band (int i, int j, int width, int m, int sp, int step)
{                                                                    /* 391 */
    double fL, fR;                                                     /* 390 */

    if (sp == 0)
        fL = fR = twotomquarter (m);
    else if (sp & 1) {
        fR = twotomquarter (m);
        if (i_scale == 0)
            sp = (sp + 1) >> 1;
        else
            sp = sp + 1;
        fL = twotomquarter (m - sp);
    }
    else {
        fL = twotomquarter (m);
        if (i_scale == 0)
            sp = sp >> 1;
        fR = twotomquarter (m - sp);
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

static int
is_zero_band (int band)
{                                                                    /* 395 */
    int i;

    for (i = 0; i < width[0][1][band]; i++)
        if (uv[1][start[0][1][band] + i] != 0)
            return 0;
    return 1;
}


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

extern int
mp3_open (std::ifstream* myFile_ ,                                                           /*  11 */
        int (*input_read) (int id, std::ifstream *, void *buffer, size_t size)     /*  12 */
        , mp3_options * option_pointer                            /*  18 */
)
{
    int id;

    stream *s;

    if (input_read == nullptr)
        return MP3_ERROR_NO_INPUT;
    id = 0;                                                            /*  44 */
    while (streams[id] != nullptr) {
        id++;
        if (id >= STREAMS)
            return MP3_ERROR_TOO_MANY;
    }
    s = (stream *) malloc (sizeof (stream));                           /*  46 */
    if (s == nullptr)
        return MP3_ERROR_MEMORY;
    streams[id] = s;
    memset (s, 0, sizeof (*s));                                        /*  48 */
    s->info.id = id;                                                   /*  25 */
    s->info.bit_per_sample = sizeof (mp3_sample) * 8;                  /*  34 */
    s->input_read = input_read;                                        /*  51 */
    if (option_pointer != nullptr)
        s->options = *option_pointer;
    s->byte_pointer = s->buffer;                                       /* 150 */
    s->start = s->buffer;                                              /* 152 */
    s->finish = s->buffer;
    s->info.changes = MP3_INFO_FRAME | MP3_INFO_ONCE;                  /* 183 */
    s->myFile = myFile_;
    return id;
}

extern int
mp3_close (int id)
{                                                                    /*  16 */
    stream *s;

    if (id >= STREAMS || id < 0)                                       /*  52 */
        return MP3_ERROR_NO_ID;
    s = streams[id];
    if (s == nullptr)
        return MP3_ERROR_NOT_OPEN;
    free (s);
    streams[id] = nullptr;
    return 0;
}

extern int
mp3_read (int id, mp3_sample * buffer, int size)
{                                                                    /*  13 */
    stream *s;

    if (id >= STREAMS || id < 0)                                       /*  52 */
        return MP3_ERROR_NO_ID;
    s = streams[id];
    if (s == nullptr)
        return MP3_ERROR_NOT_OPEN;
    if (s->state & END_OF_OUTPUT)                                      /*  56 */
        return MP3_ERROR_DONE;
    if (!(buffer == nullptr && size == 0 && s->options.info_callback != nullptr)        /*  30 */
            ) {
        if (buffer == nullptr)
            return MP3_ERROR_NO_BUFFER;
        if (size < MP3_MIN_BUFFER)
            return MP3_ERROR_NO_SIZE;
    }
    s->info.changes |= MP3_INFO_READ;
    s->info.samples = 0;
    do {
        if (s->frame == nullptr) {                                          /*  62 */
            s->frame = s->synchronize ();
            if (s->frame == nullptr) {
                if (!(s->options.flags & MP3_DONT_FLUSH)) {                  /*  72 */
                    int n;

                    {
                        double *v;                                               /*  76 */

                        int i, ch, sb;

                        for (n = 0, ch = 0; ch < s->info.channels; ch++) {
                            v = s->w[ch] + s->offset[ch];
                            for (i = n; i < WINDOWBLOCKS - 1; i++)
                                for (sb = 0; sb < SUBBANDS; sb++)
                                    if (v[i * SUBBANDS + sb] != 0.0) {
                                        n = i + 1;
                                        break;
                                    }
                        }
                    }
                    s->output_silence ( buffer + s->info.samples, n);
                }
                s->state = s->state | END_OF_OUTPUT;                         /*  60 */
                return s->info.samples;
            }
        }
        s->byte_pointer = s->frame + HEADER_SIZE;                        /* 156 */
        s->bit_offset = 0;
        output_mode = s->info.layer;                                     /*  65 */
        s->info.frame_position = s->buffer_position + (s->frame - s->buffer);       /* 155 */
        if (s->info.layer == 2) {                                        /* 191 */
            if (s->info.version == MP3_V1_0) {                             /* 192 */
                int bit_rate_per_channel = s->info.bit_rate / s->info.channels;

                if (bit_rate_per_channel <= 48000) {
                    if (s->info.sample_rate == 32000) {
                        static const signed char nbal[32] = { 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3 };        /* 419 */
                        static const signed char nbit[12][16] = {
                                {0, -5, -7, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                                {0, -5, -7, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7}
                        };
                        s->sblimit[0] = 12;
                        s->nbal = nbal;
                        s->nbit = nbit;
                    }
                    else {                                                     /* 199 */
                        static const signed char nbal[32] = { 4, 4, 3, 3, 3, 3, 3, 3 };
                        static const signed char nbit[8][16] = {
                                {0, -5, -7, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                                {0, -5, -7, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7},
                                {0, -5, -7, -10, 4, 5, 6, 7}
                        };
                        s->sblimit[0] = 8;
                        s->nbal = nbal;
                        s->nbit = nbit;
                    }
                }
                else if (bit_rate_per_channel <= 80000 || s->info.sample_rate == 48000) {       /* 417 */
                    static const char signed nbal[32] =
                            { 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
                              3, 2, 2, 2, 2 };
                    static const char signed nbit[27][16] = {
                            {0, -5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                            {0, -5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                            {0, -5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16}
                    };
                    s->sblimit[0] = 27;
                    s->nbal = nbal;
                    s->nbit = nbit;
                }
                else {                                                       /* 418 */
                    static const char signed nbal[32] =
                            { 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
                              3, 2, 2, 2, 2, 2, 2, 2 };
                    static const char signed nbit[30][16] = {
                            {0, -5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                            {0, -5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                            {0, -5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16}, {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16}, {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16}, {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16}, {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16}, {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 3, -10, 4, 5, 16}, {0, -5, -7, 3, -10, 4, 5, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16},
                            {0, -5, -7, 16}
                    };
                    s->sblimit[0] = 30;
                    s->nbal = nbal;
                    s->nbit = nbit;
                }
            }
            if (s->info.version != MP3_V1_0) {                             /* 372 */
                static const char signed nbal[32] = { 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };      /* 420 */
                static const char signed nbit[30][16] = {
                        {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                        {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                        {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                        {0, -5, -7, 3, -10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10, 4, 5, 6, 7},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10},
                        {0, -5, -7, -10}
                };
                s->sblimit[0] = 30;
                s->nbal = nbal;
                s->nbit = nbit;
            }
            if (s->info.mode != MP3_JOINT_STEREO)                          /* 194 */
                s->info.bound = s->sblimit[0];
        }
        if (output_mode == LAYER_III && s->info.version != MP3_V1_0)     /* 397 */
            output_mode = LAYER_III_V2;
        {
            static const int side_info_size[2][2] = {                      /* 430 */
                    {17, 9},
                    {32, 17}
            };
            s->info.fixed_size = HEADER_SIZE + 2 * s->info.crc_protected +
                                 side_info_size[s->info.version == MP3_V1_0][s->info.mode == MP3_MONO];
        }
        if (s->info.crc_protected) {                                     /*  99 */
            if (s->getbit ( 16) != s->crc_check ()) {
                output_mode = REPAIR | SKIP;
                s->info.changes |= MP3_INFO_CRC;
            }
        }
        if (s->info.layer == 3) {                                        /* 322 */
            int reservoir_size;

            if (s->info.version == MP3_V1_0)                               /* 320 */
                reservoir_size = s->getbit ( 9);
            if (s->info.version != MP3_V1_0)                               /* 373 */
                reservoir_size = s->getbit ( 8);
            s->main_data_start = s->frame - reservoir_size;
            if (s->main_data_start < s->start) {
                s->main_data_start = s->start;                               /* 323 */
                output_mode = SKIP;
            }
        }
        if (s->options.info_callback != nullptr) {                          /*  61 */
            if ((buffer == nullptr && size == 0 && s->options.info_callback != nullptr)     /*  30 */
                    ) {
                int result = s->options.info_callback (&(s->info));          /* 180 */

                if (result < 0)
                    return result;
                if (result & 0xFF) {                                         /* 181 */
                    s->options.flags = (s->options.flags & ~0xFF) | (result & 0xFF);
                    result = result & ~0xFF;
                }
                return 0;
            }
            if ((s->info.header ^ s->previous_header) & 0xFF180CC0)        /* 184 */
                s->info.changes |= MP3_INFO_PCM;
            if ((s->info.header ^ s->previous_header) & 0xFF1FF0CF)
                s->info.changes |= MP3_INFO_MPG;
            s->previous_header = s->info.header;
            if (s->options.flags & s->info.changes) {
                int result = s->options.info_callback (&(s->info));

                s->info.changes = MP3_INFO_FRAME;
                if (result != MP3_CONTINUE) {
                    if (result < 0)                                            /* 187 */
                        return result;
                    if (result & 0xFF) {                                       /* 181 */
                        s->options.flags = (s->options.flags & ~0xFF) | (result & 0xFF);
                        result = result & ~0xFF;
                    }
                    if (result == MP3_BREAK)                                   /* 189 */
                        return s->info.samples;
                    if (result & MP3_MUTE)                                     /* 190 */
                        output_mode = MUTE;
                    else if (result & MP3_REPEAT)
                        output_mode = REPEAT;
                    else if (result & MP3_REPAIR)
                        output_mode = REPAIR;
                    if (result & MP3_SKIP)
                        output_mode = output_mode | SKIP;
                }
            }
        }
        switch (output_mode) {
            case LAYER_I:                                                   /*  66 */
            { {
                    int sb, ch;                                                /* 106 */

                    for (sb = 0; sb < s->info.bound; sb++)
                        for (ch = 0; ch < s->info.channels; ch++) {
                            int n;

                            {
                                int a = s->getbit ( 4);                               /* 108 */

                                if (a == 15) {
                                    s->state = s->state | BITALLOCATION_ERROR;
                                    a = 14;
                                }
                                if (a > 0)
                                    n = a + 1;
                                else
                                    n = 0;
                            }
                            side_info[sb][ch].bit_allocation = n;
                        }
                }
                {
                    int sb;                                                      /* 107 */

                    for (sb = s->info.bound; sb < SUBBANDS; sb++) {
                        int n;

                        {
                            int a = s->getbit (4);                                   /* 108 */

                            if (a == 15) {
                                s->state = s->state | BITALLOCATION_ERROR;
                                a = 14;
                            }
                            if (a > 0)
                                n = a + 1;
                            else
                                n = 0;
                        }
                        side_info[sb][0].bit_allocation = n;
                        side_info[sb][1].bit_allocation = n;
                    }
                }
                {
                    int sb, ch;                                                  /* 111 */

                    for (sb = 0; sb < SUBBANDS; sb++)
                        for (ch = 0; ch < s->info.channels; ch++) {
                            side_information *si;

                            si = &(side_info[sb][ch]);
                            {
                                int n = si->bit_allocation;                            /* 112 */

                                if (n != 0) {
                                    int i;

                                    i = s->getbit ( 6);                                   /* 113 */
                                    if (i >= 63) {
                                        s->state = s->state | SCALEFACTOR_ERROR;
                                        i = 62;
                                    }
                                    si->mfactor[0] = mfactors[n][i];                     /* 115 */
                                }
                            }
                        }
                }
            }
                s->layer_I_decode_samples ();
                s->output_blocks ( buffer + s->info.samples, 12);
                s->frame = s->next_frame ();
                break;
            case LAYER_II:                                                  /* 216 */
            { {
                    int sb, ch;                                                /* 195 */

                    for (sb = 0; sb < s->info.bound; sb++)
                        for (ch = 0; ch < s->info.channels; ch++) {
                            int n, nbal = s->nbal[sb];                             /* 198 */

                            if (nbal > 0) {
                                int i;

                                i = s->getbit ( nbal);
                                n = s->nbit[sb][i];
                            }
                            else
                                n = 0;
                            side_info[sb][ch].bit_allocation = n;
                        }
                    for (; sb < s->sblimit[0]; sb++) {
                        ch = 0;
                        {
                            int n, nbal = s->nbal[sb];                             /* 198 */

                            if (nbal > 0) {
                                int i;

                                i = s->getbit ( nbal);
                                n = s->nbit[sb][i];
                            }
                            else
                                n = 0;
                            side_info[sb][ch].bit_allocation = n;
                        }
                        side_info[sb][1].bit_allocation = side_info[sb][0].bit_allocation;
                    }
                }
                {
                    int sb, ch;                                                  /* 203 */

                    for (sb = 0; sb < s->sblimit[0]; sb++)
                        for (ch = 0; ch < s->info.channels; ch++)
                            if (side_info[sb][ch].bit_allocation != 0)
                                side_info[sb][ch].scfi = s->getbit ( 2);
                }
                {
                    int sb, ch;                                                  /* 206 */

                    for (sb = 0; sb < s->sblimit[0]; sb++)
                        for (ch = 0; ch < s->info.channels; ch++) {
                            side_information *si = &(side_info[sb][ch]);

                            int n = si->bit_allocation;

                            int scfi = si->scfi;

                            if (n != 0) {
                                if (n < 0) {
                                    if (n == -5)                                         /* 207 */
                                        n = 2;
                                    else if (n == -7)
                                        n = 1;
                                    else if (n == -10)
                                        n = 0;
                                }
                                {
                                    int i;                                               /* 205 */

                                    if (scfi == 0) {
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[0] = mfactors[n][i];
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[1] = mfactors[n][i];
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[2] = mfactors[n][i];
                                    }
                                    else if (scfi == 1) {
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[1] = si->mfactor[0] = mfactors[n][i];
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[2] = mfactors[n][i];
                                    }
                                    else if (scfi == 2) {
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[0] = si->mfactor[1] = si->mfactor[2] =
                                                mfactors[n][i];
                                    }
                                    else {
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[0] = mfactors[n][i];
                                        i = s->getbit ( 6);                                 /* 113 */
                                        if (i >= 63) {
                                            s->state = s->state | SCALEFACTOR_ERROR;
                                            i = 62;
                                        }
                                        si->mfactor[2] = si->mfactor[1] = mfactors[n][i];
                                    }
                                }
                            }
                            else
                                si->mfactor[0] = si->mfactor[1] = si->mfactor[2] = 1.0;
                        }
                }
            }
                {
                    int g;

                    for (g = 0; g < GROUPS; g++) {
                        s->layer_II_decode_samples (g);
                        s->output_blocks ( buffer + s->info.samples, 12);
                    }
                }
                s->frame = s->next_frame ();
                break;
            case LAYER_III:                                                 /* 319 */
            {                                                              /* 332 */
                if (s->info.mode == MP3_MONO)                                /* 333 */
                    s->getbit ( 5);
                else
                    s->getbit ( 3);
                {
                    int ch, group;                                             /* 335 */

                    for (ch = 0; ch < s->info.channels; ch++)
                        for (group = 0; group < BANDGROUPS; group++)
                            s->share[ch][group] = s->getbit ( 1);
                }
                {
                    int ch, gr;                                                /* 338 */

                    for (gr = 0; gr < 2; gr++)
                        for (ch = 0; ch < s->info.channels; ch++) {
                            main_data_bit[gr][ch] = s->getbit ( 12);                /* 339 */
                            big_values[gr][ch] = s->getbit ( 9);                    /* 341 */
                            if (big_values[gr][ch] > FREQUENCIES / 2)
                                big_values[gr][ch] = FREQUENCIES / 2;
                            global_gain[gr][ch] = s->getbit ( 8);                   /* 343 */
                            s->slength[gr][ch] = slength_v1[s->getbit ( 4)];        /* 344 */
                            window_switching[gr][ch] = s->getbit ( 1);              /* 347 */
                            if (window_switching[gr][ch]) {                        /* 349 */
                                s->block_type[gr][ch] = s->getbit ( 2);               /* 356 */
                                if (s->block_type[gr][ch] == SHORT_BLOCK) {
                                    s->share[ch][0] = s->share[ch][1] = s->share[ch][2] =
                                    s->share[ch][3] = 0;
                                }
                                s->mixed_block[gr][ch] = s->getbit ( 1);
                                width[gr][ch] = width_table[s->info.version][s->info.frequency_index][s->mixed_block[gr][ch]][s->block_type[gr][ch] == SHORT_BLOCK];    /* 245 */
                                start[gr][ch] =
                                        start_table[s->info.version][s->info.frequency_index][s->
                                                mixed_block
                                        [gr]
                                        [ch]]
                                        [s->block_type[gr][ch] == SHORT_BLOCK];
                                s->bigtable[gr][ch][0] = s->getbit ( 5);
                                s->bigtable[gr][ch][1] = s->getbit ( 5);
                                {
                                    int i;

                                    for (i = 0; i < SUBBLOCKS; i++)
                                        subblock_gain[gr][ch][i] = s->getbit ( 3);
                                }
                                {
                                    int *pairs = s->bigpairs[gr][ch];                  /* 357 */

                                    int max_pairs;

                                    if (s->block_type[gr][ch] == SHORT_BLOCK
                                        && !s->mixed_block[gr][ch])
                                        max_pairs = start[gr][ch][9] / 2;
                                    else
                                        max_pairs = start[gr][ch][8] / 2;
                                    if (big_values[gr][ch] <= max_pairs) {
                                        pairs[0] = big_values[gr][ch];
                                        pairs[1] = 0;
                                        pairs[2] = 0;
                                    }
                                    else {
                                        pairs[0] = max_pairs;
                                        pairs[1] = big_values[gr][ch] - max_pairs;
                                        pairs[2] = 0;
                                    }
                                }
                            }
                            else { {
                                    int region;                                        /* 351 */

                                    s->block_type[gr][ch] = LONG_BLOCK;
                                    s->mixed_block[gr][ch] = 0;
                                    width[gr][ch] = width_table[s->info.version][s->info.frequency_index][s->mixed_block[gr][ch]][s->block_type[gr][ch] == SHORT_BLOCK];  /* 245 */
                                    start[gr][ch] =
                                            start_table[s->info.version][s->info.frequency_index][s->
                                                    mixed_block
                                            [gr]
                                            [ch]]
                                            [s->block_type[gr][ch] == SHORT_BLOCK];
                                    for (region = 0; region < REGIONS; region++)
                                        s->bigtable[gr][ch][region] = s->getbit ( 5);
                                }
                                {
                                    int region0, region1;                                /* 354 */

                                    region0 = s->getbit ( 4) + 1;
                                    region1 = s->getbit ( 3) + 1;
                                    {
                                        int *pairs = s->bigpairs[gr][ch];                  /* 355 */

                                        pairs[0] = start[gr][ch][region0] / 2;
                                        if (pairs[0] >= big_values[gr][ch]) {
                                            pairs[0] = big_values[gr][ch];
                                            pairs[1] = pairs[2] = 0;
                                        }
                                        else {
                                            pairs[1] = start[gr][ch][region0 + region1] / 2;
                                            if (pairs[1] >= big_values[gr][ch]) {
                                                pairs[1] = big_values[gr][ch] - pairs[0];
                                                pairs[2] = 0;
                                            }
                                            else {
                                                pairs[1] = pairs[1] - pairs[0];
                                                pairs[2] = big_values[gr][ch] - pairs[0] - pairs[1];
                                            }
                                        }
                                    }
                                }
                            }
                            s->slimit[gr][ch] = slimit_v1[s->block_type[gr][ch] == SHORT_BLOCK]       /* 345 */
                            [s->mixed_block[gr][ch]];
                            if (s->getbit ( 1) == 0)
                                preemphasis[gr][ch] = zero_table;                    /* 260 */
                            else
                                preemphasis[gr][ch] = preemphasis_table;             /* 259 */
                            if (s->getbit ( 1) == 0)
                                scale_shift[gr][ch] = 1;                             /* 254 */
                            else
                                scale_shift[gr][ch] = 2;                             /* 255 */
                            s->smalltable_A[gr][ch] = (s->getbit ( 1) == 0);        /* 360 */
                        }
                }
            }
                {
                    int gr;                                                      /* 362 */

                    int main_data_position = 0;                                  /* 326 */

                    { {
                            int size = s->frame - s->main_data_start;                /* 325 */

                            memmove (s->frame - size + s->info.fixed_size,
                                     s->frame - size, size);
                            s->main_data_start = s->main_data_start + s->info.fixed_size;
                            s->start = s->start + s->info.fixed_size;
                        }
                        s->byte_pointer = s->main_data_start;
                        s->bit_offset = 0;
                    }
                    for (gr = 0; gr < GRANULES; gr++) {
                        int ch;                                                    /* 363 */

                        for (ch = 0; ch < s->info.channels; ch++) { /* 364 */  {
                                if (gr == 0 || s->block_type[gr][ch] == SHORT_BLOCK || s->block_type[0][ch] == SHORT_BLOCK) {     /* 365 */
                                    const signed char *slength = s->slength[gr][ch];            /* 367 */

                                    const char *slimit = s->slimit[gr][ch];

                                    int band = 0, slen = *slength, slim = *slimit;

                                    do {
                                        if (band >= slim) {
                                            slen = *++slength;
                                            slim = *++slimit;
                                        }
                                        else { {
                                                int m;                                         /* 368 */

                                                if (slen == 0)
                                                    m = 0;
                                                else
                                                    m = s->getbit ( slen);
                                                s->sfimax[ch][band] = (2 << slen) - 1;
                                                s->sfi[ch][band] = m;
                                            }
                                            band++;
                                        }
                                    }
                                    while (slen >= 0);
                                }
                                else {
                                    const signed char *slength = s->slength[gr][ch];            /* 337 */

                                    int band;

                                    int slen = slength[0];

                                    if (!(s->share[ch][0]))
                                        for (band = 0; band < 6; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                    if (!(s->share[ch][1]))
                                        for (band = 6; band < 11; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                    slen = slength[1];
                                    if (!(s->share[ch][2]))
                                        for (band = 11; band < 16; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                    if (!(s->share[ch][3]))
                                        for (band = 16; band < 21; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                }
                            }
                            {
                                short int *u = uv[ch];                                   /* 369 */

                                int region;

                                for (region = 0; region < REGIONS; region++) {
                                    int k = s->bigtable[gr][ch][region];                   /* 370 */

                                    if (k == 0) {
                                        int n;                                               /* 371 */

                                        for (n = 0; n < s->bigpairs[gr][ch][region]; n++)
                                            u[2 * n] = u[2 * n + 1] = 0;
                                    }
                                    else if (k <= 12)
                                        s->decode_big ( u, k, s->bigpairs[gr][ch][region]);
                                    else
                                        s->decode_very_big ( u, k, s->bigpairs[gr][ch][region]);
                                    u = u + 2 * s->bigpairs[gr][ch][region];
                                }
                                if (s->smalltable_A[gr][ch])
                                    u = s->decode_small_A ( u, main_data_position + main_data_bit[gr][ch] - ((s->byte_pointer - s->main_data_start) * 8 + s->bit_offset)       /* 328 */
                                            , FREQUENCIES - (u - uv[ch]));
                                else
                                    u = s->decode_small_B ( u, main_data_position + main_data_bit[gr][ch] - ((s->byte_pointer - s->main_data_start) * 8 + s->bit_offset)       /* 328 */
                                            , FREQUENCIES - (u - uv[ch]));
                                ulimit[ch] = u - uv[ch];
                                while (u < &(uv[ch][FREQUENCIES]))
                                    *u++ = 0;
                            }
                            main_data_position = main_data_position + main_data_bit[gr][ch];      /* 327 */
                            s->byte_pointer = s->main_data_start + main_data_position / 8;
                            s->bit_offset = main_data_position % 8;
                        }
                        {
                            if (s->info.mode == MP3_JOINT_STEREO &&                  /* 290 */
                                (s->info.i_stereo || s->info.ms_stereo)) {
                                unsigned char stereo_mode[BANDS];                      /* 278 */

                                int band_limit, first_short_band, long_limit;

                                {
                                    if (s->block_type[gr][0] == SHORT_BLOCK) {           /* 279 */
                                        if (s->mixed_block[gr][0])
                                            first_short_band = boundary_table[s->info.version];
                                        else
                                            first_short_band = 0;
                                    }
                                    else
                                        first_short_band = BANDS;
                                }
                                {
                                    int band;                                            /* 283 */

                                    int limit = max (ulimit[0], ulimit[1]);

                                    if (!s->info.i_stereo)
                                        for (band = 0; start[gr][0][band] < limit; band++)
                                            stereo_mode[band] = MID_SIDE;
                                    else {
                                        unsigned char low_frequency_mode = s->info.ms_stereo ? MID_SIDE : STEREO;     /* 287 */

                                        for (band = 0; start[gr][1][band] < ulimit[1]; band++)
                                            stereo_mode[band] = low_frequency_mode;
                                        if (s->info.version == MP3_V1_0) {
                                            for (; start[gr][0][band] < ulimit[0]; band++)
                                                if (s->sfi[1][band] < 7)
                                                    stereo_mode[band] = INTENSITY;
                                                else
                                                    stereo_mode[band] = low_frequency_mode;
                                        }
                                        else { {
                                                int k;                                         /* 394 */

                                                k = band - 2;
                                                while (k >= first_short_band && is_zero_band (k)) {
                                                    if (s->sfi[1][k] < s->sfimax[1][k])
                                                        stereo_mode[k] = INTENSITY_V2;
                                                    else
                                                        stereo_mode[k] = low_frequency_mode;
                                                    k = k - 3;
                                                }
                                                k = band - 3;
                                                while (k >= first_short_band && is_zero_band (k)) {
                                                    if (s->sfi[1][k] < s->sfimax[1][k])
                                                        stereo_mode[k] = INTENSITY_V2;
                                                    else
                                                        stereo_mode[k] = low_frequency_mode;
                                                    k = k - 3;
                                                }
                                            }
                                            for (; start[gr][0][band] < ulimit[0]; band++)
                                                if (s->sfi[1][band] < s->sfimax[1][band])
                                                    stereo_mode[band] = INTENSITY_V2;
                                                else
                                                    stereo_mode[band] = low_frequency_mode;
                                        }
                                    }
                                    band_limit = band;
                                    while (band < BANDS)
                                        stereo_mode[band++] = NONE;
                                }
                                long_limit = min (band_limit, first_short_band);
                                {
                                    int i = 0, band = 0;

                                    while (band < long_limit) {
                                        int ch, size = width[gr][0][band];                 /* 288 */

                                        if (stereo_mode[band] == STEREO) {
                                            ch = 0;
                                            qs_band (0, i, i, size, (4 * OUTPUT_EXPONENT     /* 262 */
                                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                                     +(-((s->sfi[ch][band]   /* 252 */
                                                                          +preemphasis[gr][ch][band])
                                                             << scale_shift[gr][ch] /* 256 */
                                                     ))
                                                     )
                                                    , 1);
                                            ch = 1;
                                            qs_band (1, i, i, size, (4 * OUTPUT_EXPONENT     /* 262 */
                                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                                     +(-((s->sfi[ch][band]   /* 252 */
                                                                          +preemphasis[gr][ch][band])
                                                             << scale_shift[gr][ch] /* 256 */
                                                     ))
                                                     )
                                                    , 1);
                                        }
                                        else if (stereo_mode[band] == INTENSITY)
                                            qs_intensity_band (i, i, size, (ch = 0, (4 * OUTPUT_EXPONENT        /* 262 */
                                                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                                                     +(-((s->sfi[ch][band]      /* 252 */
                                                                                          +preemphasis
                                                                                          [gr][ch]
                                                                                          [band])
                                                    << scale_shift[gr][ch] /* 256 */
                                            ))
                                            )
                                            ), s->sfi[1][band], 1);
                                        else if (stereo_mode[band] == MID_SIDE) {
                                            int mM, mS;

                                            ch = 0;
                                            mM = (4 * OUTPUT_EXPONENT                        /* 262 */
                                                  + (global_gain[gr][ch] - 210)              /* 251 */
                                                  +(-((s->sfi[ch][band]                      /* 252 */
                                                       +preemphasis[gr][ch][band])
                                                    << scale_shift[gr][ch]                 /* 256 */
                                            ))
                                            );
                                            ch = 1;
                                            mS = (4 * OUTPUT_EXPONENT                        /* 262 */
                                                  + (global_gain[gr][ch] - 210)              /* 251 */
                                                  +(-((s->sfi[ch][band]                      /* 252 */
                                                       +preemphasis[gr][ch][band])
                                                    << scale_shift[gr][ch]                 /* 256 */
                                            ))
                                            );
                                            qs_mid_side_band (i, i, size, mM, mS, 1);
                                        }
                                        else if (stereo_mode[band] == INTENSITY_V2)
                                            qs_intensity_v2_band (i, i, size, (ch = 0, (4 * OUTPUT_EXPONENT     /* 262 */
                                                                                        + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                        +(-((s->sfi[ch][band]   /* 252 */
                                                                                             +preemphasis
                                                                                             [gr][ch]
                                                                                             [band])
                                                    << scale_shift[gr][ch]      /* 256 */
                                            ))
                                            )
                                            ), s->sfi[1][band], 1);
                                        else
                                            break;
                                        i = i + size;
                                        band++;
                                    }
                                    while (band < band_limit) {
                                        int ch, k, j = i, size = width[gr][0][band];       /* 289 */

                                        for (k = 0; k < 3; k++, band++, j++, i = i + size)
                                            if (stereo_mode[band] == STEREO) {
                                                qs_band (0, i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                                qs_band (1, i, j, size, (ch = 1, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                            }
                                            else if (stereo_mode[band] == INTENSITY)
                                                qs_intensity_band (i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT      /* 265 */
                                                                                         + (global_gain[gr][ch] - 210)    /* 251 */
                                                                                         -8 *
                                                                                          subblock_gain[gr]
                                                                                          [ch][k]
                                                                                         - ((s->sfi[ch][band]     /* 252 */
                                                ) << scale_shift[gr][ch]      /* 256 */
                                                                                         )
                                                )
                                                ), s->sfi[1][band], 3);
                                            else if (stereo_mode[band] == MID_SIDE) {
                                                int mM, mS;

                                                ch = 0;
                                                mM = (4 * OUTPUT_EXPONENT                      /* 265 */
                                                      + (global_gain[gr][ch] - 210)            /* 251 */
                                                      -8 * subblock_gain[gr][ch][k]
                                                      - ((s->sfi[ch][band]                     /* 252 */
                                                ) << scale_shift[gr][ch]              /* 256 */
                                                      )
                                                );
                                                ch = 1;
                                                mS = (4 * OUTPUT_EXPONENT                      /* 265 */
                                                      + (global_gain[gr][ch] - 210)            /* 251 */
                                                      -8 * subblock_gain[gr][ch][k]
                                                      - ((s->sfi[ch][band]                     /* 252 */
                                                ) << scale_shift[gr][ch]              /* 256 */
                                                      )
                                                );
                                                qs_mid_side_band (i, j, size, mM, mS, 3);
                                            }
                                            else if (stereo_mode[band] == INTENSITY_V2)
                                                qs_intensity_v2_band (i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT   /* 265 */
                                                                                            + (global_gain[gr][ch] - 210) /* 251 */
                                                                                            -8 *
                                                                                             subblock_gain
                                                                                             [gr][ch][k]
                                                                                            - ((s->sfi[ch][band]  /* 252 */
                                                ) << scale_shift[gr][ch]   /* 256 */
                                                                                            )
                                                )
                                                ), s->sfi[1][band], 3);
                                            else {
                                                qs_band (0, i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                                qs_band (1, i, j, size, (ch = 1, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                            }
                                    }
                                    ulimit[0] = ulimit[1] = start[gr][0][band];          /* 285 */
                                }
                            }
                            else {
                                int ch;

                                for (ch = 0; ch < s->info.channels; ch++)
                                    if (s->block_type[gr][ch] != SHORT_BLOCK)
                                        s->qs ( gr, ch, 0, 0, ulimit[ch]);
                                    else {
                                        if (s->mixed_block[gr][ch]) {
                                            int band = boundary_table[s->info.version];

                                            int limit = start[gr][ch][band];

                                            s->qs ( gr, ch, 0, 0, limit);
                                            band = s->qs_short ( gr, ch, band, limit, ulimit[ch]);
                                            ulimit[ch] = start[gr][ch][band];
                                        }
                                        else {
                                            int band = s->qs_short ( gr, ch, 0, 0, ulimit[ch]);

                                            ulimit[ch] = start[gr][ch][band];
                                        }
                                    }
                            }
                        }
                        for (ch = 0; ch < s->info.channels; ch++) {                /* 297 */
                            s->sblimit[ch] = (ulimit[ch] + SUBFREQUENCIES - 1) / SUBFREQUENCIES;        /* 292 */
                            {
                                int i;

                                for (i = ulimit[ch]; i < s->sblimit[ch] * SUBFREQUENCIES; i++)
                                    z[ch][i] = 0.0;
                            }
                            if (s->block_type[gr][ch] != SHORT_BLOCK || s->mixed_block[gr][ch]) {
                                int k;

                                if (s->block_type[gr][ch] == SHORT_BLOCK
                                    && s->mixed_block[gr][ch])
                                    k = 1;
                                else
                                    k = SUBBANDS - 1;
                                {
                                    if (s->sblimit[ch] < SUBBANDS &&                     /* 293 */
                                        ulimit[ch] >
                                        s->sblimit[ch] * SUBFREQUENCIES + 1 - SUBFREQUENCIES / 2) {
                                        int i;

                                        for (i = 0; i < SUBFREQUENCIES; i++)
                                            z[ch][s->sblimit[ch] * SUBFREQUENCIES + i] = 0.0;
                                        s->sblimit[ch]++;
                                    }
                                }
                                if (k > s->sblimit[ch] - 1)
                                    k = s->sblimit[ch] - 1;
                                for (; k > 0; k--) {
                                    int i;

                                    double *pHi = z[ch] + k * SUBFREQUENCIES;

                                    double *pLo = pHi - 1;

                                    for (i = 0; i < 8; i++, pLo--, pHi++) {
                                        double zHi, zLo, c, d;

                                        zLo = *pLo;
                                        zHi = *pHi;
                                        c = alias_coefficients[i].c;
                                        d = alias_coefficients[i].d;
                                        *pLo = zLo * c - zHi * d;
                                        *pHi = zHi * c + zLo * d;
                                    }
                                }
                            }
                            if (s->block_type[gr][ch] == LONG_BLOCK) {               /* 234 */
                                int sb;                                                /* 223 */

                                for (sb = 0; sb < SUBBANDS; sb++) {
                                    double t[18];

                                    if (sb < s->sblimit[ch])
                                        dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                    else {
                                        int i;                                             /* 224 */

                                        for (i = 0; i < 18; i++)
                                            t[i] = 0.0;
                                    }
                                    {
                                        double *tprime = s->tprime[ch][sb];                /* 222 */

                                        double (*ytilde)[CHANNELS][SUBBANDS];

                                        ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                        ytilde[0][0][0] =
                                                0.016141215071515232 * t[9] -
                                                0.33876269203672448 * tprime[8];
                                        ytilde[1][0][0] =
                                                0.053603179340958942 * t[10] -
                                                0.3124222244434797 * tprime[7];
                                        ytilde[2][0][0] =
                                                0.10070713368098732 * t[11] -
                                                0.28939587067395209 * tprime[6];
                                        ytilde[3][0][0] =
                                                0.16280817683323032 * t[12] -
                                                0.2688008181734919 * tprime[5];
                                        ytilde[4][0][0] = 0.25 * t[13] - 0.25 * tprime[4];
                                        ytilde[5][0][0] =
                                                0.38388735268512201 * t[14] -
                                                0.23251417322569559 * tprime[3];
                                        ytilde[6][0][0] =
                                                0.62061144742717948 * t[15] -
                                                0.21596714512355858 * tprime[2];
                                        ytilde[7][0][0] =
                                                1.1659756150367535 * t[16] -
                                                0.20004978874768518 * tprime[1];
                                        ytilde[8][0][0] =
                                                3.8720752882039943 * t[17] -
                                                0.18449493249753878 * tprime[0];
                                        ytilde[9][0][0] =
                                                -4.2256286787972681 * t[17] -
                                                0.16905845809573499 * tprime[0];
                                        ytilde[10][0][0] =
                                                -1.5195290056300272 * t[16] -
                                                0.15350360184558858 * tprime[1];
                                        ytilde[11][0][0] =
                                                -0.97416483802045324 * t[15] -
                                                0.13758624546971519 * tprime[2];
                                        ytilde[12][0][0] =
                                                -0.73744074327839577 * t[14] -
                                                0.12103921736757817 * tprime[3];
                                        ytilde[13][0][0] =
                                                -0.60355339059327376 * t[13] -
                                                0.10355339059327376 * tprime[4];
                                        ytilde[14][0][0] =
                                                -0.51636156742650409 * t[12] -
                                                0.084752572419781859 * tprime[5];
                                        ytilde[15][0][0] =
                                                -0.45426052427426109 * t[11] -
                                                0.064157519919321673 * tprime[6];
                                        ytilde[16][0][0] =
                                                -0.4071565699342327 * t[10] -
                                                0.041131166149794058 * tprime[7];
                                        ytilde[17][0][0] =
                                                -0.36969460566478899 * t[9] -
                                                0.014790698556549281 * tprime[8];
                                    }
                                    {
                                        double *tprime = s->tprime[ch][sb];                /* 221 */

                                        tprime[0] = t[0];
                                        tprime[1] = t[1];
                                        tprime[2] = t[2];
                                        tprime[3] = t[3];
                                        tprime[4] = t[4];
                                        tprime[5] = t[5];
                                        tprime[6] = t[6];
                                        tprime[7] = t[7];
                                        tprime[8] = t[8];
                                    }
                                }
                            }
                            else {
                                int sb = 0;

                                double t[18];

                                if (s->mixed_block[gr][ch]) {
                                    int long_subbands = start[gr][ch][boundary_table[s->info.version]] / SUBFREQUENCIES     /* 429 */
                                    ;

                                    for (sb = 0; sb < long_subbands; sb++) {
                                        if (sb < s->sblimit[ch])
                                            dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 222 */

                                            double (*ytilde)[CHANNELS][SUBBANDS];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    0.016141215071515232 * t[9] -
                                                    0.33876269203672448 * tprime[8];
                                            ytilde[1][0][0] =
                                                    0.053603179340958942 * t[10] -
                                                    0.3124222244434797 * tprime[7];
                                            ytilde[2][0][0] =
                                                    0.10070713368098732 * t[11] -
                                                    0.28939587067395209 * tprime[6];
                                            ytilde[3][0][0] =
                                                    0.16280817683323032 * t[12] -
                                                    0.2688008181734919 * tprime[5];
                                            ytilde[4][0][0] = 0.25 * t[13] - 0.25 * tprime[4];
                                            ytilde[5][0][0] =
                                                    0.38388735268512201 * t[14] -
                                                    0.23251417322569559 * tprime[3];
                                            ytilde[6][0][0] =
                                                    0.62061144742717948 * t[15] -
                                                    0.21596714512355858 * tprime[2];
                                            ytilde[7][0][0] =
                                                    1.1659756150367535 * t[16] -
                                                    0.20004978874768518 * tprime[1];
                                            ytilde[8][0][0] =
                                                    3.8720752882039943 * t[17] -
                                                    0.18449493249753878 * tprime[0];
                                            ytilde[9][0][0] =
                                                    -4.2256286787972681 * t[17] -
                                                    0.16905845809573499 * tprime[0];
                                            ytilde[10][0][0] =
                                                    -1.5195290056300272 * t[16] -
                                                    0.15350360184558858 * tprime[1];
                                            ytilde[11][0][0] =
                                                    -0.97416483802045324 * t[15] -
                                                    0.13758624546971519 * tprime[2];
                                            ytilde[12][0][0] =
                                                    -0.73744074327839577 * t[14] -
                                                    0.12103921736757817 * tprime[3];
                                            ytilde[13][0][0] =
                                                    -0.60355339059327376 * t[13] -
                                                    0.10355339059327376 * tprime[4];
                                            ytilde[14][0][0] =
                                                    -0.51636156742650409 * t[12] -
                                                    0.084752572419781859 * tprime[5];
                                            ytilde[15][0][0] =
                                                    -0.45426052427426109 * t[11] -
                                                    0.064157519919321673 * tprime[6];
                                            ytilde[16][0][0] =
                                                    -0.4071565699342327 * t[10] -
                                                    0.041131166149794058 * tprime[7];
                                            ytilde[17][0][0] =
                                                    -0.36969460566478899 * t[9] -
                                                    0.014790698556549281 * tprime[8];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 221 */

                                            tprime[0] = t[0];
                                            tprime[1] = t[1];
                                            tprime[2] = t[2];
                                            tprime[3] = t[3];
                                            tprime[4] = t[4];
                                            tprime[5] = t[5];
                                            tprime[6] = t[6];
                                            tprime[7] = t[7];
                                            tprime[8] = t[8];
                                        }
                                    }
                                }
                                if (s->block_type[gr][ch] == SHORT_BLOCK)
                                    for (; sb < SUBBANDS; sb++) {                        /* 230 */
                                        double t[18];

                                        if (sb < s->sblimit[ch]) {
                                            dct6 (z[ch] + sb * 18, t);
                                            dct6 (z[ch] + sb * 18 + 1, t + 6);
                                            dct6 (z[ch] + sb * 18 + 2, t + 12);
                                        }
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double (*ytilde)[CHANNELS][SUBBANDS];            /* 229 */

                                            double *tprime = s->tprime[ch][sb];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    -0.62484444888695941 * tprime[2] +
                                                    0.10720635868191788 * tprime[6];
                                            ytilde[1][0][0] = -0.5 * tprime[1] + 0.5 * tprime[7];
                                            ytilde[2][0][0] =
                                                    -0.40009957749537036 * tprime[0] +
                                                    2.3319512300735069 * tprime[8];
                                            ytilde[3][0][0] =
                                                    -0.30700720369117716 * tprime[0] -
                                                    3.0390580112600545 * tprime[8];
                                            ytilde[4][0][0] =
                                                    -0.20710678118654752 * tprime[1] -
                                                    1.2071067811865475 * tprime[7];
                                            ytilde[5][0][0] =
                                                    -0.082262332299588115 * tprime[2] -
                                                    0.81431313986846541 * tprime[6];
                                            ytilde[6][0][0] =
                                                    0.107206358681917884 * t[3] -
                                                    0.62484444888695941 * tprime[5];
                                            ytilde[7][0][0] = 0.5 * t[4] - 0.5 * tprime[4];
                                            ytilde[8][0][0] =
                                                    2.33195123007350693 * t[5] -
                                                    0.40009957749537036 * tprime[3];
                                            ytilde[9][0][0] =
                                                    -3.03905801126005446 * t[5] -
                                                    0.30700720369117716 * tprime[3];
                                            ytilde[10][0][0] =
                                                    -1.20710678118654752 * t[4] -
                                                    0.20710678118654752 * tprime[4];
                                            ytilde[11][0][0] =
                                                    -0.81431313986846541 * t[3] -
                                                    0.082262332299588115 * tprime[5];
                                            ytilde[12][0][0] =
                                                    -0.62484444888695941 * t[2] + 0.10720635868191788 * t[9];
                                            ytilde[13][0][0] = -0.5 * t[1] + 0.5 * t[10];
                                            ytilde[14][0][0] =
                                                    -0.40009957749537036 * t[0] + 2.3319512300735069 * t[11];
                                            ytilde[15][0][0] =
                                                    -0.307007203691177164 * t[0] - 3.0390580112600545 * t[11];
                                            ytilde[16][0][0] =
                                                    -0.207106781186547524 * t[1] - 1.2071067811865475 * t[10];
                                            ytilde[17][0][0] =
                                                    -0.082262332299588115 * t[2] - 0.81431313986846541 * t[9];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 228 */

                                            tprime[0] = t[6];
                                            tprime[1] = t[7];
                                            tprime[2] = t[8];
                                            tprime[3] = t[12];
                                            tprime[4] = t[13];
                                            tprime[5] = t[14];
                                            tprime[6] = t[15];
                                            tprime[7] = t[16];
                                            tprime[8] = t[17];
                                        }
                                    }
                                else if (s->block_type[gr][ch] == START_BLOCK) {
                                    for (; sb < SUBBANDS; sb++) {
                                        if (sb < s->sblimit[ch])
                                            dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 222 */

                                            double (*ytilde)[CHANNELS][SUBBANDS];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    0.016141215071515232 * t[9] -
                                                    0.33876269203672448 * tprime[8];
                                            ytilde[1][0][0] =
                                                    0.053603179340958942 * t[10] -
                                                    0.3124222244434797 * tprime[7];
                                            ytilde[2][0][0] =
                                                    0.10070713368098732 * t[11] -
                                                    0.28939587067395209 * tprime[6];
                                            ytilde[3][0][0] =
                                                    0.16280817683323032 * t[12] -
                                                    0.2688008181734919 * tprime[5];
                                            ytilde[4][0][0] = 0.25 * t[13] - 0.25 * tprime[4];
                                            ytilde[5][0][0] =
                                                    0.38388735268512201 * t[14] -
                                                    0.23251417322569559 * tprime[3];
                                            ytilde[6][0][0] =
                                                    0.62061144742717948 * t[15] -
                                                    0.21596714512355858 * tprime[2];
                                            ytilde[7][0][0] =
                                                    1.1659756150367535 * t[16] -
                                                    0.20004978874768518 * tprime[1];
                                            ytilde[8][0][0] =
                                                    3.8720752882039943 * t[17] -
                                                    0.18449493249753878 * tprime[0];
                                            ytilde[9][0][0] =
                                                    -4.2256286787972681 * t[17] -
                                                    0.16905845809573499 * tprime[0];
                                            ytilde[10][0][0] =
                                                    -1.5195290056300272 * t[16] -
                                                    0.15350360184558858 * tprime[1];
                                            ytilde[11][0][0] =
                                                    -0.97416483802045324 * t[15] -
                                                    0.13758624546971519 * tprime[2];
                                            ytilde[12][0][0] =
                                                    -0.73744074327839577 * t[14] -
                                                    0.12103921736757817 * tprime[3];
                                            ytilde[13][0][0] =
                                                    -0.60355339059327376 * t[13] -
                                                    0.10355339059327376 * tprime[4];
                                            ytilde[14][0][0] =
                                                    -0.51636156742650409 * t[12] -
                                                    0.084752572419781859 * tprime[5];
                                            ytilde[15][0][0] =
                                                    -0.45426052427426109 * t[11] -
                                                    0.064157519919321673 * tprime[6];
                                            ytilde[16][0][0] =
                                                    -0.4071565699342327 * t[10] -
                                                    0.041131166149794058 * tprime[7];
                                            ytilde[17][0][0] =
                                                    -0.36969460566478899 * t[9] -
                                                    0.014790698556549281 * tprime[8];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 231 */

                                            tprime[0] =
                                                    0.34021759826241347 * t[5] + 0.46631140047156712 * t[6];
                                            tprime[1] =
                                                    0.19134171618254489 * t[4] + 0.53794025136387497 * t[7];
                                            tprime[2] =
                                                    0.054289373022064996 * t[3] + 0.53342619745279293 * t[8];
                                            tprime[3] = 0.49619469809174553 * t[0];
                                            tprime[4] = 0.46592582628906829 * t[1];
                                            tprime[5] = 0.40630778703664996 * t[2];
                                            tprime[6] =
                                                    0.31642165430726767 * t[3] - 0.053886988879063598 * t[8];
                                            tprime[7] =
                                                    0.19134171618254489 * t[4] - 0.092295955641257263 * t[7];
                                            tprime[8] =
                                                    0.058372111545827878 * t[5] - 0.047107017561910602 * t[6];
                                        }
                                    }
                                }
                                else if (s->block_type[gr][ch] == STOP_BLOCK) {
                                    for (; sb < SUBBANDS; sb++) {
                                        if (sb < s->sblimit[ch])
                                            dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 232 */

                                            double (*ytilde)[CHANNELS][SUBBANDS];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    -0.62484444888695941 * tprime[2] +
                                                    0.10720635868191788 * tprime[6];
                                            ytilde[1][0][0] = -0.5 * tprime[1] + 0.5 * tprime[7];
                                            ytilde[2][0][0] =
                                                    -0.40009957749537036 * tprime[0] +
                                                    2.3319512300735069 * tprime[8];
                                            ytilde[3][0][0] =
                                                    -0.30700720369117716 * tprime[0] -
                                                    3.0390580112600545 * tprime[8];
                                            ytilde[4][0][0] =
                                                    -0.20710678118654752 * tprime[1] -
                                                    1.2071067811865475 * tprime[7];
                                            ytilde[5][0][0] =
                                                    -0.082262332299588115 * tprime[2] -
                                                    0.81431313986846541 * tprime[6];
                                            ytilde[6][0][0] =
                                                    0.15076513703422529 * t[15] -
                                                    0.62484444888695941 * tprime[5];
                                            ytilde[7][0][0] =
                                                    0.73296291314453414 * t[16] - 0.5 * tprime[4];
                                            ytilde[8][0][0] =
                                                    3.4890530666445053 * t[17] -
                                                    0.40009957749537036 * tprime[3];
                                            ytilde[9][0][0] =
                                                    -4.5470224836405378 * t[17] -
                                                    0.30700720369117716 * tprime[3];
                                            ytilde[10][0][0] =
                                                    -1.7695290056300272 * t[16] -
                                                    0.20710678118654752 * tprime[4];
                                            ytilde[11][0][0] =
                                                    -1.1451749096832876 * t[15] -
                                                    0.082262332299588115 * tprime[5];
                                            ytilde[12][0][0] = -0.83137738085576039 * t[14];
                                            ytilde[13][0][0] = -0.65328148243818826 * t[13];
                                            ytilde[14][0][0] = -0.54142014255005005 * t[12];
                                            ytilde[15][0][0] = -0.46528974917589448 * t[11];
                                            ytilde[16][0][0] = -0.41066990792614539 * t[10];
                                            ytilde[17][0][0] = -0.37004680823056527 * t[9];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 221 */

                                            tprime[0] = t[0];
                                            tprime[1] = t[1];
                                            tprime[2] = t[2];
                                            tprime[3] = t[3];
                                            tprime[4] = t[4];
                                            tprime[5] = t[5];
                                            tprime[6] = t[6];
                                            tprime[7] = t[7];
                                            tprime[8] = t[8];
                                        }
                                    }
                                }
                            }
                            {
                                int i, sb;                                             /* 296 */

                                for (sb = 1; sb < SUBBANDS; sb = sb + 2)
                                    for (i = 1; i < 18; i = i + 2)
                                        y[i][ch][sb] = -y[i][ch][sb];
                            }
                        }
                        s->output_blocks ( buffer + s->info.samples, SUBFREQUENCIES);
                    }
                    ;                                                            /* 141 */
                    ;
                }
                s->frame = s->next_frame ();
                break;
            case LAYER_III_V2:                                              /* 398 */
                if (s->info.mode == MP3_MONO)                                  /* 374 */
                    s->getbit ( 1);
                else
                    s->getbit ( 2);
                {
                    const int gr = 0;                                            /* 378 */

                    int ch;

                    for (ch = 0; ch < s->info.channels; ch++) {
                        main_data_bit[gr][ch] = s->getbit ( 12);                    /* 339 */
                        big_values[gr][ch] = s->getbit ( 9);                        /* 341 */
                        if (big_values[gr][ch] > FREQUENCIES / 2)
                            big_values[gr][ch] = FREQUENCIES / 2;
                        global_gain[gr][ch] = s->getbit ( 8);                       /* 343 */
                        {
                            int sfc = s->getbit ( 9);                                 /* 379 */

                            if (s->info.i_stereo && ch == 1) {
                                i_scale = sfc & 1;
                                s->slength[0][ch] = slength_v2i[sfc >> 1];
                            }
                            else
                                s->slength[0][ch] = slength_v2[sfc];
                        }
                        window_switching[gr][ch] = s->getbit ( 1);                  /* 347 */
                        if (window_switching[gr][ch]) {                            /* 349 */
                            s->block_type[gr][ch] = s->getbit ( 2);                   /* 356 */
                            if (s->block_type[gr][ch] == SHORT_BLOCK) {
                                s->share[ch][0] = s->share[ch][1] = s->share[ch][2] =
                                s->share[ch][3] = 0;
                            }
                            s->mixed_block[gr][ch] = s->getbit ( 1);
                            width[gr][ch] = width_table[s->info.version][s->info.frequency_index][s->mixed_block[gr][ch]][s->block_type[gr][ch] == SHORT_BLOCK];        /* 245 */
                            start[gr][ch] =
                                    start_table[s->info.version][s->info.frequency_index][s->
                                            mixed_block
                                    [gr][ch]]
                                    [s->block_type[gr][ch] == SHORT_BLOCK];
                            s->bigtable[gr][ch][0] = s->getbit ( 5);
                            s->bigtable[gr][ch][1] = s->getbit ( 5);
                            {
                                int i;

                                for (i = 0; i < SUBBLOCKS; i++)
                                    subblock_gain[gr][ch][i] = s->getbit ( 3);
                            }
                            {
                                int *pairs = s->bigpairs[gr][ch];                      /* 357 */

                                int max_pairs;

                                if (s->block_type[gr][ch] == SHORT_BLOCK
                                    && !s->mixed_block[gr][ch])
                                    max_pairs = start[gr][ch][9] / 2;
                                else
                                    max_pairs = start[gr][ch][8] / 2;
                                if (big_values[gr][ch] <= max_pairs) {
                                    pairs[0] = big_values[gr][ch];
                                    pairs[1] = 0;
                                    pairs[2] = 0;
                                }
                                else {
                                    pairs[0] = max_pairs;
                                    pairs[1] = big_values[gr][ch] - max_pairs;
                                    pairs[2] = 0;
                                }
                            }
                        }
                        else { {
                                int region;                                            /* 351 */

                                s->block_type[gr][ch] = LONG_BLOCK;
                                s->mixed_block[gr][ch] = 0;
                                width[gr][ch] = width_table[s->info.version][s->info.frequency_index][s->mixed_block[gr][ch]][s->block_type[gr][ch] == SHORT_BLOCK];      /* 245 */
                                start[gr][ch] =
                                        start_table[s->info.version][s->info.frequency_index][s->
                                                mixed_block
                                        [gr][ch]]
                                        [s->block_type[gr][ch] == SHORT_BLOCK];
                                for (region = 0; region < REGIONS; region++)
                                    s->bigtable[gr][ch][region] = s->getbit ( 5);
                            }
                            {
                                int region0, region1;                                    /* 354 */

                                region0 = s->getbit ( 4) + 1;
                                region1 = s->getbit ( 3) + 1;
                                {
                                    int *pairs = s->bigpairs[gr][ch];                      /* 355 */

                                    pairs[0] = start[gr][ch][region0] / 2;
                                    if (pairs[0] >= big_values[gr][ch]) {
                                        pairs[0] = big_values[gr][ch];
                                        pairs[1] = pairs[2] = 0;
                                    }
                                    else {
                                        pairs[1] = start[gr][ch][region0 + region1] / 2;
                                        if (pairs[1] >= big_values[gr][ch]) {
                                            pairs[1] = big_values[gr][ch] - pairs[0];
                                            pairs[2] = 0;
                                        }
                                        else {
                                            pairs[1] = pairs[1] - pairs[0];
                                            pairs[2] = big_values[gr][ch] - pairs[0] - pairs[1];
                                        }
                                    }
                                }
                            }
                        }
                        if (s->info.i_stereo && ch == 1)                           /* 385 */
                            s->slimit[0][ch] = slimit_v2i[(int) (s->slength[0][ch][7])  /* 381 */
                            ][s->block_type[0][ch] == SHORT_BLOCK][s->mixed_block[0][ch]];
                        else
                            s->slimit[0][ch] = slimit_v2[(int) (s->slength[0][ch][7])   /* 381 */
                            ][s->block_type[0][ch] == SHORT_BLOCK][s->mixed_block[0][ch]];
                        if (s->slength[0][ch][6] == 0)
                            preemphasis[gr][ch] = zero_table;                        /* 260 */
                        else
                            preemphasis[gr][ch] = preemphasis_table;                 /* 259 */
                        if (s->getbit ( 1) == 0)
                            scale_shift[gr][ch] = 1;                                 /* 254 */
                        else
                            scale_shift[gr][ch] = 2;                                 /* 255 */
                        s->smalltable_A[gr][ch] = (s->getbit ( 1) == 0);            /* 360 */
                    }
                }
                {
                    const int gr = 0;                                            /* 399 */

                    int main_data_position = 0;                                  /* 326 */

                    { {
                            int size = s->frame - s->main_data_start;                /* 325 */

                            memmove (s->frame - size + s->info.fixed_size,
                                     s->frame - size, size);
                            s->main_data_start = s->main_data_start + s->info.fixed_size;
                            s->start = s->start + s->info.fixed_size;
                        }
                        s->byte_pointer = s->main_data_start;
                        s->bit_offset = 0;
                    }
                    {
                        int ch;                                                    /* 363 */

                        for (ch = 0; ch < s->info.channels; ch++) { /* 364 */  {
                                if (gr == 0 || s->block_type[gr][ch] == SHORT_BLOCK || s->block_type[0][ch] == SHORT_BLOCK) {     /* 365 */
                                    const signed char *slength = s->slength[gr][ch];            /* 367 */

                                    const char *slimit = s->slimit[gr][ch];

                                    int band = 0, slen = *slength, slim = *slimit;

                                    do {
                                        if (band >= slim) {
                                            slen = *++slength;
                                            slim = *++slimit;
                                        }
                                        else { {
                                                int m;                                         /* 368 */

                                                if (slen == 0)
                                                    m = 0;
                                                else
                                                    m = s->getbit ( slen);
                                                s->sfimax[ch][band] = (2 << slen) - 1;
                                                s->sfi[ch][band] = m;
                                            }
                                            band++;
                                        }
                                    }
                                    while (slen >= 0);
                                }
                                else {
                                    const signed char *slength = s->slength[gr][ch];            /* 337 */

                                    int band;

                                    int slen = slength[0];

                                    if (!(s->share[ch][0]))
                                        for (band = 0; band < 6; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                    if (!(s->share[ch][1]))
                                        for (band = 6; band < 11; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                    slen = slength[1];
                                    if (!(s->share[ch][2]))
                                        for (band = 11; band < 16; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                    if (!(s->share[ch][3]))
                                        for (band = 16; band < 21; band++) {
                                            int m;                                           /* 368 */

                                            if (slen == 0)
                                                m = 0;
                                            else
                                                m = s->getbit ( slen);
                                            s->sfimax[ch][band] = (2 << slen) - 1;
                                            s->sfi[ch][band] = m;
                                        }
                                }
                            }
                            {
                                short int *u = uv[ch];                                   /* 369 */

                                int region;

                                for (region = 0; region < REGIONS; region++) {
                                    int k = s->bigtable[gr][ch][region];                   /* 370 */

                                    if (k == 0) {
                                        int n;                                               /* 371 */

                                        for (n = 0; n < s->bigpairs[gr][ch][region]; n++)
                                            u[2 * n] = u[2 * n + 1] = 0;
                                    }
                                    else if (k <= 12)
                                        s->decode_big ( u, k, s->bigpairs[gr][ch][region]);
                                    else
                                        s->decode_very_big ( u, k, s->bigpairs[gr][ch][region]);
                                    u = u + 2 * s->bigpairs[gr][ch][region];
                                }
                                if (s->smalltable_A[gr][ch])
                                    u = s->decode_small_A ( u, main_data_position + main_data_bit[gr][ch] - ((s->byte_pointer - s->main_data_start) * 8 + s->bit_offset)       /* 328 */
                                            , FREQUENCIES - (u - uv[ch]));
                                else
                                    u = s->decode_small_B ( u, main_data_position + main_data_bit[gr][ch] - ((s->byte_pointer - s->main_data_start) * 8 + s->bit_offset)       /* 328 */
                                            , FREQUENCIES - (u - uv[ch]));
                                ulimit[ch] = u - uv[ch];
                                while (u < &(uv[ch][FREQUENCIES]))
                                    *u++ = 0;
                            }
                            main_data_position = main_data_position + main_data_bit[gr][ch];      /* 327 */
                            s->byte_pointer = s->main_data_start + main_data_position / 8;
                            s->bit_offset = main_data_position % 8;
                        }
                        {
                            if (s->info.mode == MP3_JOINT_STEREO &&                  /* 290 */
                                (s->info.i_stereo || s->info.ms_stereo)) {
                                unsigned char stereo_mode[BANDS];                      /* 278 */

                                int band_limit, first_short_band, long_limit;

                                {
                                    if (s->block_type[gr][0] == SHORT_BLOCK) {           /* 279 */
                                        if (s->mixed_block[gr][0])
                                            first_short_band = boundary_table[s->info.version];
                                        else
                                            first_short_band = 0;
                                    }
                                    else
                                        first_short_band = BANDS;
                                }
                                {
                                    int band;                                            /* 283 */

                                    int limit = max (ulimit[0], ulimit[1]);

                                    if (!s->info.i_stereo)
                                        for (band = 0; start[gr][0][band] < limit; band++)
                                            stereo_mode[band] = MID_SIDE;
                                    else {
                                        unsigned char low_frequency_mode = s->info.ms_stereo ? MID_SIDE : STEREO;     /* 287 */

                                        for (band = 0; start[gr][1][band] < ulimit[1]; band++)
                                            stereo_mode[band] = low_frequency_mode;
                                        if (s->info.version == MP3_V1_0) {
                                            for (; start[gr][0][band] < ulimit[0]; band++)
                                                if (s->sfi[1][band] < 7)
                                                    stereo_mode[band] = INTENSITY;
                                                else
                                                    stereo_mode[band] = low_frequency_mode;
                                        }
                                        else { {
                                                int k;                                         /* 394 */

                                                k = band - 2;
                                                while (k >= first_short_band && is_zero_band (k)) {
                                                    if (s->sfi[1][k] < s->sfimax[1][k])
                                                        stereo_mode[k] = INTENSITY_V2;
                                                    else
                                                        stereo_mode[k] = low_frequency_mode;
                                                    k = k - 3;
                                                }
                                                k = band - 3;
                                                while (k >= first_short_band && is_zero_band (k)) {
                                                    if (s->sfi[1][k] < s->sfimax[1][k])
                                                        stereo_mode[k] = INTENSITY_V2;
                                                    else
                                                        stereo_mode[k] = low_frequency_mode;
                                                    k = k - 3;
                                                }
                                            }
                                            for (; start[gr][0][band] < ulimit[0]; band++)
                                                if (s->sfi[1][band] < s->sfimax[1][band])
                                                    stereo_mode[band] = INTENSITY_V2;
                                                else
                                                    stereo_mode[band] = low_frequency_mode;
                                        }
                                    }
                                    band_limit = band;
                                    while (band < BANDS)
                                        stereo_mode[band++] = NONE;
                                }
                                long_limit = min (band_limit, first_short_band);
                                {
                                    int i = 0, band = 0;

                                    while (band < long_limit) {
                                        int ch, size = width[gr][0][band];                 /* 288 */

                                        if (stereo_mode[band] == STEREO) {
                                            ch = 0;
                                            qs_band (0, i, i, size, (4 * OUTPUT_EXPONENT     /* 262 */
                                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                                     +(-((s->sfi[ch][band]   /* 252 */
                                                                          +preemphasis[gr][ch][band])
                                                             << scale_shift[gr][ch] /* 256 */
                                                     ))
                                                     )
                                                    , 1);
                                            ch = 1;
                                            qs_band (1, i, i, size, (4 * OUTPUT_EXPONENT     /* 262 */
                                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                                     +(-((s->sfi[ch][band]   /* 252 */
                                                                          +preemphasis[gr][ch][band])
                                                             << scale_shift[gr][ch] /* 256 */
                                                     ))
                                                     )
                                                    , 1);
                                        }
                                        else if (stereo_mode[band] == INTENSITY)
                                            qs_intensity_band (i, i, size, (ch = 0, (4 * OUTPUT_EXPONENT        /* 262 */
                                                                                     + (global_gain[gr][ch] - 210)      /* 251 */
                                                                                     +(-((s->sfi[ch][band]      /* 252 */
                                                                                          +preemphasis
                                                                                          [gr][ch]
                                                                                          [band])
                                                    << scale_shift[gr][ch] /* 256 */
                                            ))
                                            )
                                            ), s->sfi[1][band], 1);
                                        else if (stereo_mode[band] == MID_SIDE) {
                                            int mM, mS;

                                            ch = 0;
                                            mM = (4 * OUTPUT_EXPONENT                        /* 262 */
                                                  + (global_gain[gr][ch] - 210)              /* 251 */
                                                  +(-((s->sfi[ch][band]                      /* 252 */
                                                       +preemphasis[gr][ch][band])
                                                    << scale_shift[gr][ch]                 /* 256 */
                                            ))
                                            );
                                            ch = 1;
                                            mS = (4 * OUTPUT_EXPONENT                        /* 262 */
                                                  + (global_gain[gr][ch] - 210)              /* 251 */
                                                  +(-((s->sfi[ch][band]                      /* 252 */
                                                       +preemphasis[gr][ch][band])
                                                    << scale_shift[gr][ch]                 /* 256 */
                                            ))
                                            );
                                            qs_mid_side_band (i, i, size, mM, mS, 1);
                                        }
                                        else if (stereo_mode[band] == INTENSITY_V2)
                                            qs_intensity_v2_band (i, i, size, (ch = 0, (4 * OUTPUT_EXPONENT     /* 262 */
                                                                                        + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                        +(-((s->sfi[ch][band]   /* 252 */
                                                                                             +preemphasis
                                                                                             [gr][ch]
                                                                                             [band])
                                                    << scale_shift[gr][ch]      /* 256 */
                                            ))
                                            )
                                            ), s->sfi[1][band], 1);
                                        else
                                            break;
                                        i = i + size;
                                        band++;
                                    }
                                    while (band < band_limit) {
                                        int ch, k, j = i, size = width[gr][0][band];       /* 289 */

                                        for (k = 0; k < 3; k++, band++, j++, i = i + size)
                                            if (stereo_mode[band] == STEREO) {
                                                qs_band (0, i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                                qs_band (1, i, j, size, (ch = 1, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                            }
                                            else if (stereo_mode[band] == INTENSITY)
                                                qs_intensity_band (i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT      /* 265 */
                                                                                         + (global_gain[gr][ch] - 210)    /* 251 */
                                                                                         -8 *
                                                                                          subblock_gain[gr]
                                                                                          [ch][k]
                                                                                         - ((s->sfi[ch][band]     /* 252 */
                                                ) << scale_shift[gr][ch]      /* 256 */
                                                                                         )
                                                )
                                                ), s->sfi[1][band], 3);
                                            else if (stereo_mode[band] == MID_SIDE) {
                                                int mM, mS;

                                                ch = 0;
                                                mM = (4 * OUTPUT_EXPONENT                      /* 265 */
                                                      + (global_gain[gr][ch] - 210)            /* 251 */
                                                      -8 * subblock_gain[gr][ch][k]
                                                      - ((s->sfi[ch][band]                     /* 252 */
                                                ) << scale_shift[gr][ch]              /* 256 */
                                                      )
                                                );
                                                ch = 1;
                                                mS = (4 * OUTPUT_EXPONENT                      /* 265 */
                                                      + (global_gain[gr][ch] - 210)            /* 251 */
                                                      -8 * subblock_gain[gr][ch][k]
                                                      - ((s->sfi[ch][band]                     /* 252 */
                                                ) << scale_shift[gr][ch]              /* 256 */
                                                      )
                                                );
                                                qs_mid_side_band (i, j, size, mM, mS, 3);
                                            }
                                            else if (stereo_mode[band] == INTENSITY_V2)
                                                qs_intensity_v2_band (i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT   /* 265 */
                                                                                            + (global_gain[gr][ch] - 210) /* 251 */
                                                                                            -8 *
                                                                                             subblock_gain
                                                                                             [gr][ch][k]
                                                                                            - ((s->sfi[ch][band]  /* 252 */
                                                ) << scale_shift[gr][ch]   /* 256 */
                                                                                            )
                                                )
                                                ), s->sfi[1][band], 3);
                                            else {
                                                qs_band (0, i, j, size, (ch = 0, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                                qs_band (1, i, j, size, (ch = 1, (4 * OUTPUT_EXPONENT     /* 265 */
                                                                                  + (global_gain[gr][ch] - 210)   /* 251 */
                                                                                  -8 *
                                                                                   subblock_gain[gr][ch][k]
                                                                                  - ((s->sfi[ch][band]    /* 252 */
                                                ) << scale_shift[gr][ch]     /* 256 */
                                                                                  )
                                                )
                                                ), 3);
                                            }
                                    }
                                    ulimit[0] = ulimit[1] = start[gr][0][band];          /* 285 */
                                }
                            }
                            else {
                                int ch;

                                for (ch = 0; ch < s->info.channels; ch++)
                                    if (s->block_type[gr][ch] != SHORT_BLOCK)
                                        s->qs ( gr, ch, 0, 0, ulimit[ch]);
                                    else {
                                        if (s->mixed_block[gr][ch]) {
                                            int band = boundary_table[s->info.version];

                                            int limit = start[gr][ch][band];

                                            s->qs ( gr, ch, 0, 0, limit);
                                            band = s->qs_short ( gr, ch, band, limit, ulimit[ch]);
                                            ulimit[ch] = start[gr][ch][band];
                                        }
                                        else {
                                            int band = s->qs_short ( gr, ch, 0, 0, ulimit[ch]);

                                            ulimit[ch] = start[gr][ch][band];
                                        }
                                    }
                            }
                        }
                        for (ch = 0; ch < s->info.channels; ch++) {                /* 297 */
                            s->sblimit[ch] = (ulimit[ch] + SUBFREQUENCIES - 1) / SUBFREQUENCIES;        /* 292 */
                            {
                                int i;

                                for (i = ulimit[ch]; i < s->sblimit[ch] * SUBFREQUENCIES; i++)
                                    z[ch][i] = 0.0;
                            }
                            if (s->block_type[gr][ch] != SHORT_BLOCK || s->mixed_block[gr][ch]) {
                                int k;

                                if (s->block_type[gr][ch] == SHORT_BLOCK
                                    && s->mixed_block[gr][ch])
                                    k = 1;
                                else
                                    k = SUBBANDS - 1;
                                {
                                    if (s->sblimit[ch] < SUBBANDS &&                     /* 293 */
                                        ulimit[ch] >
                                        s->sblimit[ch] * SUBFREQUENCIES + 1 - SUBFREQUENCIES / 2) {
                                        int i;

                                        for (i = 0; i < SUBFREQUENCIES; i++)
                                            z[ch][s->sblimit[ch] * SUBFREQUENCIES + i] = 0.0;
                                        s->sblimit[ch]++;
                                    }
                                }
                                if (k > s->sblimit[ch] - 1)
                                    k = s->sblimit[ch] - 1;
                                for (; k > 0; k--) {
                                    int i;

                                    double *pHi = z[ch] + k * SUBFREQUENCIES;

                                    double *pLo = pHi - 1;

                                    for (i = 0; i < 8; i++, pLo--, pHi++) {
                                        double zHi, zLo, c, d;

                                        zLo = *pLo;
                                        zHi = *pHi;
                                        c = alias_coefficients[i].c;
                                        d = alias_coefficients[i].d;
                                        *pLo = zLo * c - zHi * d;
                                        *pHi = zHi * c + zLo * d;
                                    }
                                }
                            }
                            if (s->block_type[gr][ch] == LONG_BLOCK) {               /* 234 */
                                int sb;                                                /* 223 */

                                for (sb = 0; sb < SUBBANDS; sb++) {
                                    double t[18];

                                    if (sb < s->sblimit[ch])
                                        dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                    else {
                                        int i;                                             /* 224 */

                                        for (i = 0; i < 18; i++)
                                            t[i] = 0.0;
                                    }
                                    {
                                        double *tprime = s->tprime[ch][sb];                /* 222 */

                                        double (*ytilde)[CHANNELS][SUBBANDS];

                                        ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                        ytilde[0][0][0] =
                                                0.016141215071515232 * t[9] -
                                                0.33876269203672448 * tprime[8];
                                        ytilde[1][0][0] =
                                                0.053603179340958942 * t[10] -
                                                0.3124222244434797 * tprime[7];
                                        ytilde[2][0][0] =
                                                0.10070713368098732 * t[11] -
                                                0.28939587067395209 * tprime[6];
                                        ytilde[3][0][0] =
                                                0.16280817683323032 * t[12] -
                                                0.2688008181734919 * tprime[5];
                                        ytilde[4][0][0] = 0.25 * t[13] - 0.25 * tprime[4];
                                        ytilde[5][0][0] =
                                                0.38388735268512201 * t[14] -
                                                0.23251417322569559 * tprime[3];
                                        ytilde[6][0][0] =
                                                0.62061144742717948 * t[15] -
                                                0.21596714512355858 * tprime[2];
                                        ytilde[7][0][0] =
                                                1.1659756150367535 * t[16] -
                                                0.20004978874768518 * tprime[1];
                                        ytilde[8][0][0] =
                                                3.8720752882039943 * t[17] -
                                                0.18449493249753878 * tprime[0];
                                        ytilde[9][0][0] =
                                                -4.2256286787972681 * t[17] -
                                                0.16905845809573499 * tprime[0];
                                        ytilde[10][0][0] =
                                                -1.5195290056300272 * t[16] -
                                                0.15350360184558858 * tprime[1];
                                        ytilde[11][0][0] =
                                                -0.97416483802045324 * t[15] -
                                                0.13758624546971519 * tprime[2];
                                        ytilde[12][0][0] =
                                                -0.73744074327839577 * t[14] -
                                                0.12103921736757817 * tprime[3];
                                        ytilde[13][0][0] =
                                                -0.60355339059327376 * t[13] -
                                                0.10355339059327376 * tprime[4];
                                        ytilde[14][0][0] =
                                                -0.51636156742650409 * t[12] -
                                                0.084752572419781859 * tprime[5];
                                        ytilde[15][0][0] =
                                                -0.45426052427426109 * t[11] -
                                                0.064157519919321673 * tprime[6];
                                        ytilde[16][0][0] =
                                                -0.4071565699342327 * t[10] -
                                                0.041131166149794058 * tprime[7];
                                        ytilde[17][0][0] =
                                                -0.36969460566478899 * t[9] -
                                                0.014790698556549281 * tprime[8];
                                    }
                                    {
                                        double *tprime = s->tprime[ch][sb];                /* 221 */

                                        tprime[0] = t[0];
                                        tprime[1] = t[1];
                                        tprime[2] = t[2];
                                        tprime[3] = t[3];
                                        tprime[4] = t[4];
                                        tprime[5] = t[5];
                                        tprime[6] = t[6];
                                        tprime[7] = t[7];
                                        tprime[8] = t[8];
                                    }
                                }
                            }
                            else {
                                int sb = 0;

                                double t[18];

                                if (s->mixed_block[gr][ch]) {
                                    int long_subbands = start[gr][ch][boundary_table[s->info.version]] / SUBFREQUENCIES     /* 429 */
                                    ;

                                    for (sb = 0; sb < long_subbands; sb++) {
                                        if (sb < s->sblimit[ch])
                                            dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 222 */

                                            double (*ytilde)[CHANNELS][SUBBANDS];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    0.016141215071515232 * t[9] -
                                                    0.33876269203672448 * tprime[8];
                                            ytilde[1][0][0] =
                                                    0.053603179340958942 * t[10] -
                                                    0.3124222244434797 * tprime[7];
                                            ytilde[2][0][0] =
                                                    0.10070713368098732 * t[11] -
                                                    0.28939587067395209 * tprime[6];
                                            ytilde[3][0][0] =
                                                    0.16280817683323032 * t[12] -
                                                    0.2688008181734919 * tprime[5];
                                            ytilde[4][0][0] = 0.25 * t[13] - 0.25 * tprime[4];
                                            ytilde[5][0][0] =
                                                    0.38388735268512201 * t[14] -
                                                    0.23251417322569559 * tprime[3];
                                            ytilde[6][0][0] =
                                                    0.62061144742717948 * t[15] -
                                                    0.21596714512355858 * tprime[2];
                                            ytilde[7][0][0] =
                                                    1.1659756150367535 * t[16] -
                                                    0.20004978874768518 * tprime[1];
                                            ytilde[8][0][0] =
                                                    3.8720752882039943 * t[17] -
                                                    0.18449493249753878 * tprime[0];
                                            ytilde[9][0][0] =
                                                    -4.2256286787972681 * t[17] -
                                                    0.16905845809573499 * tprime[0];
                                            ytilde[10][0][0] =
                                                    -1.5195290056300272 * t[16] -
                                                    0.15350360184558858 * tprime[1];
                                            ytilde[11][0][0] =
                                                    -0.97416483802045324 * t[15] -
                                                    0.13758624546971519 * tprime[2];
                                            ytilde[12][0][0] =
                                                    -0.73744074327839577 * t[14] -
                                                    0.12103921736757817 * tprime[3];
                                            ytilde[13][0][0] =
                                                    -0.60355339059327376 * t[13] -
                                                    0.10355339059327376 * tprime[4];
                                            ytilde[14][0][0] =
                                                    -0.51636156742650409 * t[12] -
                                                    0.084752572419781859 * tprime[5];
                                            ytilde[15][0][0] =
                                                    -0.45426052427426109 * t[11] -
                                                    0.064157519919321673 * tprime[6];
                                            ytilde[16][0][0] =
                                                    -0.4071565699342327 * t[10] -
                                                    0.041131166149794058 * tprime[7];
                                            ytilde[17][0][0] =
                                                    -0.36969460566478899 * t[9] -
                                                    0.014790698556549281 * tprime[8];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 221 */

                                            tprime[0] = t[0];
                                            tprime[1] = t[1];
                                            tprime[2] = t[2];
                                            tprime[3] = t[3];
                                            tprime[4] = t[4];
                                            tprime[5] = t[5];
                                            tprime[6] = t[6];
                                            tprime[7] = t[7];
                                            tprime[8] = t[8];
                                        }
                                    }
                                }
                                if (s->block_type[gr][ch] == SHORT_BLOCK)
                                    for (; sb < SUBBANDS; sb++) {                        /* 230 */
                                        double t[18];

                                        if (sb < s->sblimit[ch]) {
                                            dct6 (z[ch] + sb * 18, t);
                                            dct6 (z[ch] + sb * 18 + 1, t + 6);
                                            dct6 (z[ch] + sb * 18 + 2, t + 12);
                                        }
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double (*ytilde)[CHANNELS][SUBBANDS];            /* 229 */

                                            double *tprime = s->tprime[ch][sb];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    -0.62484444888695941 * tprime[2] +
                                                    0.10720635868191788 * tprime[6];
                                            ytilde[1][0][0] = -0.5 * tprime[1] + 0.5 * tprime[7];
                                            ytilde[2][0][0] =
                                                    -0.40009957749537036 * tprime[0] +
                                                    2.3319512300735069 * tprime[8];
                                            ytilde[3][0][0] =
                                                    -0.30700720369117716 * tprime[0] -
                                                    3.0390580112600545 * tprime[8];
                                            ytilde[4][0][0] =
                                                    -0.20710678118654752 * tprime[1] -
                                                    1.2071067811865475 * tprime[7];
                                            ytilde[5][0][0] =
                                                    -0.082262332299588115 * tprime[2] -
                                                    0.81431313986846541 * tprime[6];
                                            ytilde[6][0][0] =
                                                    0.107206358681917884 * t[3] -
                                                    0.62484444888695941 * tprime[5];
                                            ytilde[7][0][0] = 0.5 * t[4] - 0.5 * tprime[4];
                                            ytilde[8][0][0] =
                                                    2.33195123007350693 * t[5] -
                                                    0.40009957749537036 * tprime[3];
                                            ytilde[9][0][0] =
                                                    -3.03905801126005446 * t[5] -
                                                    0.30700720369117716 * tprime[3];
                                            ytilde[10][0][0] =
                                                    -1.20710678118654752 * t[4] -
                                                    0.20710678118654752 * tprime[4];
                                            ytilde[11][0][0] =
                                                    -0.81431313986846541 * t[3] -
                                                    0.082262332299588115 * tprime[5];
                                            ytilde[12][0][0] =
                                                    -0.62484444888695941 * t[2] + 0.10720635868191788 * t[9];
                                            ytilde[13][0][0] = -0.5 * t[1] + 0.5 * t[10];
                                            ytilde[14][0][0] =
                                                    -0.40009957749537036 * t[0] + 2.3319512300735069 * t[11];
                                            ytilde[15][0][0] =
                                                    -0.307007203691177164 * t[0] - 3.0390580112600545 * t[11];
                                            ytilde[16][0][0] =
                                                    -0.207106781186547524 * t[1] - 1.2071067811865475 * t[10];
                                            ytilde[17][0][0] =
                                                    -0.082262332299588115 * t[2] - 0.81431313986846541 * t[9];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 228 */

                                            tprime[0] = t[6];
                                            tprime[1] = t[7];
                                            tprime[2] = t[8];
                                            tprime[3] = t[12];
                                            tprime[4] = t[13];
                                            tprime[5] = t[14];
                                            tprime[6] = t[15];
                                            tprime[7] = t[16];
                                            tprime[8] = t[17];
                                        }
                                    }
                                else if (s->block_type[gr][ch] == START_BLOCK) {
                                    for (; sb < SUBBANDS; sb++) {
                                        if (sb < s->sblimit[ch])
                                            dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 222 */

                                            double (*ytilde)[CHANNELS][SUBBANDS];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    0.016141215071515232 * t[9] -
                                                    0.33876269203672448 * tprime[8];
                                            ytilde[1][0][0] =
                                                    0.053603179340958942 * t[10] -
                                                    0.3124222244434797 * tprime[7];
                                            ytilde[2][0][0] =
                                                    0.10070713368098732 * t[11] -
                                                    0.28939587067395209 * tprime[6];
                                            ytilde[3][0][0] =
                                                    0.16280817683323032 * t[12] -
                                                    0.2688008181734919 * tprime[5];
                                            ytilde[4][0][0] = 0.25 * t[13] - 0.25 * tprime[4];
                                            ytilde[5][0][0] =
                                                    0.38388735268512201 * t[14] -
                                                    0.23251417322569559 * tprime[3];
                                            ytilde[6][0][0] =
                                                    0.62061144742717948 * t[15] -
                                                    0.21596714512355858 * tprime[2];
                                            ytilde[7][0][0] =
                                                    1.1659756150367535 * t[16] -
                                                    0.20004978874768518 * tprime[1];
                                            ytilde[8][0][0] =
                                                    3.8720752882039943 * t[17] -
                                                    0.18449493249753878 * tprime[0];
                                            ytilde[9][0][0] =
                                                    -4.2256286787972681 * t[17] -
                                                    0.16905845809573499 * tprime[0];
                                            ytilde[10][0][0] =
                                                    -1.5195290056300272 * t[16] -
                                                    0.15350360184558858 * tprime[1];
                                            ytilde[11][0][0] =
                                                    -0.97416483802045324 * t[15] -
                                                    0.13758624546971519 * tprime[2];
                                            ytilde[12][0][0] =
                                                    -0.73744074327839577 * t[14] -
                                                    0.12103921736757817 * tprime[3];
                                            ytilde[13][0][0] =
                                                    -0.60355339059327376 * t[13] -
                                                    0.10355339059327376 * tprime[4];
                                            ytilde[14][0][0] =
                                                    -0.51636156742650409 * t[12] -
                                                    0.084752572419781859 * tprime[5];
                                            ytilde[15][0][0] =
                                                    -0.45426052427426109 * t[11] -
                                                    0.064157519919321673 * tprime[6];
                                            ytilde[16][0][0] =
                                                    -0.4071565699342327 * t[10] -
                                                    0.041131166149794058 * tprime[7];
                                            ytilde[17][0][0] =
                                                    -0.36969460566478899 * t[9] -
                                                    0.014790698556549281 * tprime[8];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 231 */

                                            tprime[0] =
                                                    0.34021759826241347 * t[5] + 0.46631140047156712 * t[6];
                                            tprime[1] =
                                                    0.19134171618254489 * t[4] + 0.53794025136387497 * t[7];
                                            tprime[2] =
                                                    0.054289373022064996 * t[3] + 0.53342619745279293 * t[8];
                                            tprime[3] = 0.49619469809174553 * t[0];
                                            tprime[4] = 0.46592582628906829 * t[1];
                                            tprime[5] = 0.40630778703664996 * t[2];
                                            tprime[6] =
                                                    0.31642165430726767 * t[3] - 0.053886988879063598 * t[8];
                                            tprime[7] =
                                                    0.19134171618254489 * t[4] - 0.092295955641257263 * t[7];
                                            tprime[8] =
                                                    0.058372111545827878 * t[5] - 0.047107017561910602 * t[6];
                                        }
                                    }
                                }
                                else if (s->block_type[gr][ch] == STOP_BLOCK) {
                                    for (; sb < SUBBANDS; sb++) {
                                        if (sb < s->sblimit[ch])
                                            dct18 (z[ch] + sb * SUBFREQUENCIES, t);
                                        else {
                                            int i;                                           /* 224 */

                                            for (i = 0; i < 18; i++)
                                                t[i] = 0.0;
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 232 */

                                            double (*ytilde)[CHANNELS][SUBBANDS];

                                            ytilde = (double (*)[CHANNELS][SUBBANDS]) &y[0][ch][sb];
                                            ytilde[0][0][0] =
                                                    -0.62484444888695941 * tprime[2] +
                                                    0.10720635868191788 * tprime[6];
                                            ytilde[1][0][0] = -0.5 * tprime[1] + 0.5 * tprime[7];
                                            ytilde[2][0][0] =
                                                    -0.40009957749537036 * tprime[0] +
                                                    2.3319512300735069 * tprime[8];
                                            ytilde[3][0][0] =
                                                    -0.30700720369117716 * tprime[0] -
                                                    3.0390580112600545 * tprime[8];
                                            ytilde[4][0][0] =
                                                    -0.20710678118654752 * tprime[1] -
                                                    1.2071067811865475 * tprime[7];
                                            ytilde[5][0][0] =
                                                    -0.082262332299588115 * tprime[2] -
                                                    0.81431313986846541 * tprime[6];
                                            ytilde[6][0][0] =
                                                    0.15076513703422529 * t[15] -
                                                    0.62484444888695941 * tprime[5];
                                            ytilde[7][0][0] =
                                                    0.73296291314453414 * t[16] - 0.5 * tprime[4];
                                            ytilde[8][0][0] =
                                                    3.4890530666445053 * t[17] -
                                                    0.40009957749537036 * tprime[3];
                                            ytilde[9][0][0] =
                                                    -4.5470224836405378 * t[17] -
                                                    0.30700720369117716 * tprime[3];
                                            ytilde[10][0][0] =
                                                    -1.7695290056300272 * t[16] -
                                                    0.20710678118654752 * tprime[4];
                                            ytilde[11][0][0] =
                                                    -1.1451749096832876 * t[15] -
                                                    0.082262332299588115 * tprime[5];
                                            ytilde[12][0][0] = -0.83137738085576039 * t[14];
                                            ytilde[13][0][0] = -0.65328148243818826 * t[13];
                                            ytilde[14][0][0] = -0.54142014255005005 * t[12];
                                            ytilde[15][0][0] = -0.46528974917589448 * t[11];
                                            ytilde[16][0][0] = -0.41066990792614539 * t[10];
                                            ytilde[17][0][0] = -0.37004680823056527 * t[9];
                                        }
                                        {
                                            double *tprime = s->tprime[ch][sb];              /* 221 */

                                            tprime[0] = t[0];
                                            tprime[1] = t[1];
                                            tprime[2] = t[2];
                                            tprime[3] = t[3];
                                            tprime[4] = t[4];
                                            tprime[5] = t[5];
                                            tprime[6] = t[6];
                                            tprime[7] = t[7];
                                            tprime[8] = t[8];
                                        }
                                    }
                                }
                            }
                            {
                                int i, sb;                                             /* 296 */

                                for (sb = 1; sb < SUBBANDS; sb = sb + 2)
                                    for (i = 1; i < 18; i = i + 2)
                                        y[i][ch][sb] = -y[i][ch][sb];
                            }
                        }
                        s->output_blocks ( buffer + s->info.samples, SUBFREQUENCIES);
                    }
                    ;                                                            /* 141 */
                    ;
                }
                s->frame = s->next_frame ();
                break;
            case SKIP:                                                      /* 401 */
            default:
                if (s->info.layer == 3) {
                    int size = s->frame - s->main_data_start;                    /* 325 */

                    memmove (s->frame - size + s->info.fixed_size, s->frame - size, size);
                    s->main_data_start = s->main_data_start + s->info.fixed_size;
                    s->start = s->start + s->info.fixed_size;
                }
                s->frame = s->next_frame ();
                break;
            case MUTE:                                                      /* 402 */
            {
                int n;

                if (s->info.layer == 1)
                    n = 12;                                                    /* 404 */
                else if (s->info.layer == 2)
                    n = 12 * GROUPS;
                else if (s->info.version == MP3_V1_0)
                    n = 18 * GRANULES;
                else
                    n = 18;
                s->output_silence ( buffer + s->info.samples, n);
                break;
            }
            case MUTE | SKIP:                                               /* 403 */
            {
                int n;

                if (s->info.layer == 3) {
                    int size = s->frame - s->main_data_start;                  /* 325 */

                    memmove (s->frame - size + s->info.fixed_size, s->frame - size, size);
                    s->main_data_start = s->main_data_start + s->info.fixed_size;
                    s->start = s->start + s->info.fixed_size;
                }
                if (s->info.layer == 1)
                    n = 12;                                                    /* 404 */
                else if (s->info.layer == 2)
                    n = 12 * GROUPS;
                else if (s->info.version == MP3_V1_0)
                    n = 18 * GRANULES;
                else
                    n = 18;
                s->output_silence ( buffer + s->info.samples, n);
                s->frame = s->next_frame ();
                break;
            }
            case REPEAT:                                                    /* 405 */
            {
                int n;

                if (s->info.layer == 1)
                    n = 12;                                                    /* 404 */
                else if (s->info.layer == 2)
                    n = 12 * GROUPS;
                else if (s->info.version == MP3_V1_0)
                    n = 18 * GRANULES;
                else
                    n = 18;
                s->output_repeat ( buffer + s->info.samples, n, n);
                break;
            }
            case REPEAT | SKIP:
            {
                int n;

                if (s->info.layer == 3) {
                    int size = s->frame - s->main_data_start;                  /* 325 */

                    memmove (s->frame - size + s->info.fixed_size, s->frame - size, size);
                    s->main_data_start = s->main_data_start + s->info.fixed_size;
                    s->start = s->start + s->info.fixed_size;
                }
                if (s->info.layer == 1)
                    n = 12;                                                    /* 404 */
                else if (s->info.layer == 2)
                    n = 12 * GROUPS;
                else if (s->info.version == MP3_V1_0)
                    n = 18 * GRANULES;
                else
                    n = 18;
                s->output_repeat ( buffer + s->info.samples, n, n);
                s->frame = s->next_frame ();
                break;
            }
            case REPAIR:                                                    /* 409 */
            {
                int n, d;

                if (s->info.layer == 1)
                    n = 12;                                                    /* 404 */
                else if (s->info.layer == 2)
                    n = 12 * GROUPS;
                else if (s->info.version == MP3_V1_0)
                    n = 18 * GRANULES;
                else
                    n = 18;
                {
                    int i, k, previous_offset;                                 /* 410 */

                    double *p, sum, distance, *v = s->w[0] + s->offset[0];

                    distance = SUBBANDS * ((double) ((unsigned) 1 << OUTPUT_EXPONENT))    /* 136 */
                               *((double) ((unsigned) 1 << OUTPUT_EXPONENT))            /* 136 */
                            ;
                    for (d = 1, i = 1; i < SHIFTBLOCKS - WINDOWBLOCKS + 1; i++) {
                        previous_offset = s->offset[0] + i * SUBBANDS;
                        if (previous_offset >= SHIFTSIZE)
                            previous_offset =
                                    previous_offset - SHIFTSIZE + (WINDOWBLOCKS - 1) * SUBBANDS;
                        p = s->w[0] + previous_offset;
                        for (sum = 0.0, k = 0; k < SUBBANDS; k++)
                            sum = sum + (p[k] - v[k]) * (p[k] - v[k]);
                        if (sum < distance) {
                            distance = sum;
                            d = i;
                        }
                    }
                }
                s->output_repeat ( buffer + s->info.samples, n, d);
                break;
            }
            case REPAIR | SKIP:
            {
                int n, d;

                if (s->info.layer == 3) {
                    int size = s->frame - s->main_data_start;                  /* 325 */

                    memmove (s->frame - size + s->info.fixed_size, s->frame - size, size);
                    s->main_data_start = s->main_data_start + s->info.fixed_size;
                    s->start = s->start + s->info.fixed_size;
                }
                if (s->info.layer == 1)
                    n = 12;                                                    /* 404 */
                else if (s->info.layer == 2)
                    n = 12 * GROUPS;
                else if (s->info.version == MP3_V1_0)
                    n = 18 * GRANULES;
                else
                    n = 18;
                {
                    int i, k, previous_offset;                                 /* 410 */

                    double *p, sum, distance, *v = s->w[0] + s->offset[0];

                    distance = SUBBANDS * ((double) ((unsigned) 1 << OUTPUT_EXPONENT))    /* 136 */
                               *((double) ((unsigned) 1 << OUTPUT_EXPONENT))            /* 136 */
                            ;
                    for (d = 1, i = 1; i < SHIFTBLOCKS - WINDOWBLOCKS + 1; i++) {
                        previous_offset = s->offset[0] + i * SUBBANDS;
                        if (previous_offset >= SHIFTSIZE)
                            previous_offset =
                                    previous_offset - SHIFTSIZE + (WINDOWBLOCKS - 1) * SUBBANDS;
                        p = s->w[0] + previous_offset;
                        for (sum = 0.0, k = 0; k < SUBBANDS; k++)
                            sum = sum + (p[k] - v[k]) * (p[k] - v[k]);
                        if (sum < distance) {
                            distance = sum;
                            d = i;
                        }
                    }
                }
                s->output_repeat ( buffer + s->info.samples, n, d);
                s->frame = s->next_frame ();
                break;
            }
        }
    } while (s->info.samples + MP3_MIN_BUFFER <= size);
    return s->info.samples;
}
