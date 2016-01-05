QT -= core gui

TARGET = libfoo
TEMPLATE = lib

DEFINES += LIBFOO_LIBRARY

SOURCES += libfoo.cpp

HEADERS += libfoo.h

unix {
    target.path = /usr/lib
    INSTALLS += target
}
