
#include "UDPReceiver.h"
#include <AndroidThreadPrioValues.hpp>
#include <NDKThreadHelper.hpp>
#include <arpa/inet.h>
#include <vector>
#include <sstream>
#include <array>


UDPReceiver::UDPReceiver(JavaVM* javaVm,int port,const std::string& name,int CPUPriority,const DATA_CALLBACK& onDataReceivedCallback,size_t WANTED_RCVBUF_SIZE):
        mPort(port),mName(name),WANTED_RCVBUF_SIZE(WANTED_RCVBUF_SIZE),mCPUPriority(CPUPriority),onDataReceivedCallback(onDataReceivedCallback),javaVm(javaVm){
}

void UDPReceiver::registerOnSourceIPFound(const SOURCE_IP_CALLBACK& onSourceIP) {
    this->onSourceIP=onSourceIP;
}

long UDPReceiver::getNReceivedBytes()const {
    return nReceivedBytes;
}

std::string UDPReceiver::getSourceIPAddress()const {
    return senderIP;
}

void UDPReceiver::startReceiving() {
    receiving=true;
    mUDPReceiverThread=std::make_unique<std::thread>([this]{this->receiveFromUDPLoop();} );
    NDKThreadHelper::setName(mUDPReceiverThread->native_handle(),mName.c_str());
}

void UDPReceiver::stopReceiving() {
    receiving=false;
    //this stops the recvfrom even if in blocking mode
    shutdown(mSocket,SHUT_RD);
    if(mUDPReceiverThread->joinable()){
        mUDPReceiverThread->join();
    }
    mUDPReceiverThread.reset();
}

void UDPReceiver::receiveFromUDPLoop() {
    mSocket=socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (mSocket == -1) {
        MLOGD<<"Error creating socket";
        return;
    }
    int enable = 1;
    if (setsockopt(mSocket, SOL_SOCKET, SO_REUSEADDR, &enable, sizeof(int)) < 0){
        MLOGD<<"Error setting reuse";
    }
    int recvBufferSize=0;
    socklen_t len=sizeof(recvBufferSize);
    getsockopt(mSocket, SOL_SOCKET, SO_RCVBUF, &recvBufferSize, &len);
    MLOGD<<"Default socket recv buffer is "<<recvBufferSize<<"bytes";

    if(WANTED_RCVBUF_SIZE>recvBufferSize){
        recvBufferSize=WANTED_RCVBUF_SIZE;
        if(setsockopt(mSocket, SOL_SOCKET, SO_RCVBUF, &WANTED_RCVBUF_SIZE,len)) {
            MLOGD<<"Cannot increase buffer size to "<<WANTED_RCVBUF_SIZE<<"bytes";
        }
        getsockopt(mSocket, SOL_SOCKET, SO_RCVBUF, &recvBufferSize, &len);
        MLOGD<<"Wanted "<<WANTED_RCVBUF_SIZE<<" Set "<<recvBufferSize;
    }
    NDKThreadHelper::setProcessThreadPriorityAttachDetach(javaVm, mCPUPriority, mName.c_str());
    struct sockaddr_in myaddr;
    memset((uint8_t *) &myaddr, 0, sizeof(myaddr));
    myaddr.sin_family = AF_INET;
    myaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    myaddr.sin_port = htons(mPort);
    if (bind(mSocket, (struct sockaddr *) &myaddr, sizeof(myaddr)) == -1) {
        MLOGE<<"Error binding Port; "<<mPort;
        return;
    }
    //wrap into unique pointer to avoid running out of stack
    const auto buff=std::make_unique<std::array<uint8_t,UDP_PACKET_MAX_SIZE>>();

    sockaddr_in source;
    socklen_t sourceLen= sizeof(sockaddr_in);
    std::vector<uint8_t> sequenceNumbers;

    while (receiving) {
        //TODO investigate: does a big buffer size create latency with MSG_WAITALL ?
        const ssize_t message_length = recvfrom(mSocket,buff->data(),UDP_PACKET_MAX_SIZE, MSG_WAITALL,(sockaddr*)&source,&sourceLen);
        //ssize_t message_length = recv(mSocket, buff, (size_t) mBuffsize, MSG_WAITALL);
        if (message_length > 0) { //else -1 was returned;timeout/No data received
            //LOGD("Data size %d",(int)message_length);

            //Custom protocol where first byte of udp packet is sequence number
            if(false){
                uint8_t nr;
                memcpy(&nr,buff->data(),1);
                sequenceNumbers.push_back(nr);
                if(sequenceNumbers.size()>32){
                    std::stringstream ss;
                    for(const auto seqnr:sequenceNumbers){
                        ss<<((int)seqnr)<<" ";
                    }
                    bool allInOrder=true;
                    bool allInAscendingOrder=true;
                    for(size_t i=0;i<sequenceNumbers.size()-1;i++){
                        if((sequenceNumbers.at(i)+1) != sequenceNumbers.at(i+1)){
                            allInOrder=false;
                        }
                        if((sequenceNumbers.at(i)) >= sequenceNumbers.at(i+1)){
                            allInAscendingOrder=false;
                        }
                    }
                    MLOGD<<"Seq numbers. In order "<<allInOrder<<"  In ascending order "<<allInAscendingOrder<<" values : "<<ss.str();
                    sequenceNumbers.resize(0);
                }
                onDataReceivedCallback(&buff->data()[1], (size_t)message_length);
            }else{
                onDataReceivedCallback(buff->data(), (size_t)message_length);
            }
            nReceivedBytes+=message_length;
            //The source ip stuff
            const char* p=inet_ntoa(source.sin_addr);
            std::string s1=std::string(p);
            if(senderIP!=s1){
                senderIP=s1;
            }
            if(onSourceIP!=nullptr){
                onSourceIP(p);
            }
        }else{
            if(errno != EWOULDBLOCK) {
                MLOGE<<"Error on recvfrom. errno="<<errno<<" "<<strerror(errno);
            }
        }
    }
    close(mSocket);
}

int UDPReceiver::getPort() const {
    return mPort;
}
