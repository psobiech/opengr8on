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

import java.io.IOException;

/**
 *
 */
public class TFTPPacketIOException extends IOException {
    private static final long serialVersionUID = 7959893294993558186L;

    private final int errorPacketCode;

    public TFTPPacketIOException(final int errorPacketCode, final IOException exception) {
        super(exception);

        this.errorPacketCode = errorPacketCode;
    }

    public TFTPPacketIOException(final int errorPacketCode, final String message) {
        super(message);

        this.errorPacketCode = errorPacketCode;
    }

    public int getErrorPacketCode() {
        return errorPacketCode;
    }
}
