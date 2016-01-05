TEMPLATE = app
CONFIG += console c++11
CONFIG -= app_bundle
CONFIG -= qt

exists(../custom.pri) {
    include(../custom.pri)
}

SOURCES += main.cpp
