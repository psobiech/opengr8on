/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.psobiech.opengr8on.org.apache.commons.net;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * TFTPPacket is an abstract class encapsulating the functionality common to the 5 types of TFTP packets. It also provides a static factory method that will
 * create the correct TFTP packet instance from a datagram. This can relieve the programmer from having to figure out what kind of TFTP packet is contained in a
 * datagram and create it himself.
 * <p>
 * Details regarding the TFTP protocol and the format of TFTP packets can be found in RFC 783. But the point of these classes is to keep you from having to
 * worry about the internals. Additionally, only very few people should have to care about any of the TFTPPacket classes or derived classes. Almost all users
 * should only be concerned with the {@link TFTPClient} class {@link TFTPClient#receiveFile receiveFile()} and {@link TFTPClient#sendFile sendFile()} methods.
 *
 * @see TFTPPacketException
 * @see TFTP
 */
public abstract class TFTPPacket {
    /**
     * The minimum size of a packet. This is 4 bytes. It is enough to store the opcode and block number or other required data depending on the packet type.
     */
    static final int MIN_PACKET_SIZE = 4;

    /**
     * This is the actual TFTP spec identifier and is equal to 1. Identifier returned by {@link #getType getType()} indicating a read request packet.
     */
    public static final int READ_REQUEST = 1;

    /**
     * This is the actual TFTP spec identifier and is equal to 2. Identifier returned by {@link #getType getType()} indicating a write request packet.
     */
    public static final int WRITE_REQUEST = 2;

    /**
     * This is the actual TFTP spec identifier and is equal to 3. Identifier returned by {@link #getType getType()} indicating a data packet.
     */
    public static final int DATA = 3;

    /**
     * This is the actual TFTP spec identifier and is equal to 4. Identifier returned by {@link #getType getType()} indicating an acknowledgement packet.
     */
    public static final int ACKNOWLEDGEMENT = 4;

    /**
     * This is the actual TFTP spec identifier and is equal to 5. Identifier returned by {@link #getType getType()} indicating an error packet.
     */
    public static final int ERROR = 5;

    /**
     * The TFTP data packet maximum segment size in bytes. This is 512 and is useful for those familiar with the TFTP protocol who want to use the {@link TFTP}
     * class methods to implement their own TFTP servers or clients.
     */
    public static final int SEGMENT_SIZE = 512;

    /**
     * The type of packet.
     */
    final int type;

    /**
     * The port the packet came from or is going to.
     */
    int port;

    /**
     * The host the packet is going to be sent or where it came from.
     */
    InetAddress address;

    /**
     * This constructor is not visible outside the package. It is used by subclasses within the package to initialize base data.
     *
     * @param type The type of the packet.
     * @param address The host the packet came from or is going to be sent.
     * @param port The port the packet came from or is going to be sent.
     **/
    TFTPPacket(final int type, final InetAddress address, final int port) {
        this.type = type;
        this.address = address;
        this.port = port;
    }

    /**
     * When you receive a datagram that you expect to be a TFTP packet, you use this factory method to create the proper TFTPPacket object encapsulating the
     * data contained in that datagram. This method is the only way you can instantiate a TFTPPacket derived class from a datagram.
     *
     * @param datagram The datagram containing a TFTP packet.
     * @return The TFTPPacket object corresponding to the datagram.
     * @throws TFTPPacketException If the datagram does not contain a valid TFTP packet.
     */
    public static TFTPPacket newTFTPPacket(final DatagramPacket datagram) throws TFTPPacketException {
        final byte[] data;
        TFTPPacket packet;

        if (datagram.getLength() < MIN_PACKET_SIZE) {
            throw new TFTPPacketException("Bad packet. Datagram data length is too short.");
        }

        data = datagram.getData();
        packet = switch (data[1]) {
            case READ_REQUEST -> new TFTPReadRequestPacket(datagram);
            case WRITE_REQUEST -> new TFTPWriteRequestPacket(datagram);
            case DATA -> new TFTPDataPacket(datagram);
            case ACKNOWLEDGEMENT -> new TFTPAckPacket(datagram);
            case ERROR -> new TFTPErrorPacket(datagram);
            default -> throw new TFTPPacketException("Bad packet. Invalid TFTP operator code.");
        };

        return packet;
    }

    /**
     * Returns the address of the host where the packet is going to be sent or where it came from.
     *
     * @return The type of the packet.
     */
    public final InetAddress getAddress() {
        return address;
    }

    /**
     * Returns the port where the packet is going to be sent or where it came from.
     *
     * @return The port where the packet came from or where it is going.
     */
    public final int getPort() {
        return port;
    }

    /**
     * Returns the type of the packet.
     *
     * @return The type of the packet.
     */
    public final int getType() {
        return type;
    }

    /**
     * Creates a UDP datagram containing all the TFTP packet data in the proper format. This is an abstract method, exposed to the programmer in case he wants
     * to implement his own TFTP client instead of using the {@link TFTPClient} class. Under normal circumstances, you should not have a need to call this
     * method.
     *
     * @return A UDP datagram containing the TFTP packet.
     */
    public abstract DatagramPacket newDatagram();

    /**
     * This is an abstract method only available within the package for implementing efficient datagram transport by eliminating buffering. It takes a datagram
     * as an argument, and a byte buffer in which to store the raw datagram data. Inside the method, the data should be set as the datagram's data and the
     * datagram returned.
     *
     * @param datagram The datagram to create.
     * @param data The buffer to store the packet and to use in the datagram.
     * @return The datagram argument.
     */
    abstract DatagramPacket newDatagram(DatagramPacket datagram, byte[] data);

    /**
     * Sets the host address where the packet is going to be sent.
     *
     * @param address the address to set
     */
    public final void setAddress(final InetAddress address) {
        this.address = address;
    }

    /**
     * Sets the port where the packet is going to be sent.
     *
     * @param port the port to set
     */
    public final void setPort(final int port) {
        this.port = port;
    }

    /**
     * For debugging
     *
     * @since 3.6
     */
    @Override
    public String toString() {
        return address + " " + port + " " + type;
    }
}
