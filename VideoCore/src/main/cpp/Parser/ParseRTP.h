//
// Created by Constantin on 2/6/2019.
//

#ifndef LIVE_VIDEO_10MS_ANDROID_PARSERTP_H
#define LIVE_VIDEO_10MS_ANDROID_PARSERTP_H

#include <cstdio>
#include "../NALU/NALU.hpp"

/*********************************************
 ** Parses a stream of rtp h264 data int NALUs
**********************************************/

class ParseRTP{
public:
    ParseRTP(NALU_DATA_CALLBACK cb);
public:
    void parseData(const uint8_t* data,const size_t data_length);
    void reset();
private:
    const NALU_DATA_CALLBACK cb;
    std::array<uint8_t,NALU::NALU_MAXLEN> nalu_data;
    //std::vector<uint8_t> nalu_data;
    size_t nalu_data_length=0;
};
#endif //LIVE_VIDEO_10MS_ANDROID_PARSERTP_H
