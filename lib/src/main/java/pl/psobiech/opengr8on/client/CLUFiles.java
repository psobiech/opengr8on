/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.client;

public enum CLUFiles {
    USER_LUA("a", "USER.LUA"),
    OM_LUA("a", "OM.LUA"),
    MAIN_LUA("a", "MAIN.LUA"),
    //
    INIT_LUA("a", "INIT.LUA"),
    EMERGNCY_LUA("a", "EMERGNCY.LUA"),
    //
    SETTINGS_USR("a", "settings.usr"),
    //
    CONFIG_TXT("a", "config.txt", true, false),
    CONFIG_JSON("a", "CONFIG.JSON", true, false),
    //
    MEASUREMENT_FILE("p", "meas.bin", true, false),
    //
    IMAGE_FW("m", "image.fw", false, true),
    //
    DEBUG_BIN("m", "debug.bin", true, false),
    DIAGNOSTIC_PACK_JSON("m", "DIAGNOSTIC_PACK.JSON", true, false),
    //
    CLOUD_PRIVATE_PEM("a", "CLOUD-PRIVATE.PEM"),
    CLOUD_PUBLIC_CSR("a", "CLOUD-PUBLIC.CSR"),
    CLOUD_PUBLIC_CRT("a", "CLOUD-PUBLIC.CRT"),
    CLOUD_ROOT_PEM("a", "CLOUD-ROOT.PEM"),
    //
    // LOADALL_SO("a", "loadall.so"),
    // WATCHDOG_LOG("a", "watchdog.log"),
    ;

    private final String device;

    private final String fileName;

    private final boolean readable;

    private final boolean writable;

    CLUFiles(String device, String fileName) {
        this(device, fileName, true, true);
    }

    CLUFiles(String device, String fileName, boolean readable, boolean writable) {
        this.device   = device;
        this.fileName = fileName;
        this.readable = readable;
        this.writable = writable;
    }

    public String getLocation() {
        return getDevice() + ":\\" + getFileName();
    }

    public String getDevice() {
        return device;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }
}
