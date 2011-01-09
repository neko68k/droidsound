/*
 * generic.c - Cartridge handling, generic carts.
 *
 * Written by
 *  Andreas Boose <viceteam@t-online.de>
 *
 * This file is part of VICE, the Versatile Commodore Emulator.
 * See README for copyright notice.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307  USA.
 *
 */

#include "vice.h"

#include <string.h>

#include "c64cart.h"
#define CARTRIDGE_INCLUDE_SLOTMAIN_API
#include "c64cartsystem.h"
#undef CARTRIDGE_INCLUDE_SLOTMAIN_API
#include "c64export.h"
#include "c64mem.h"
#include "cartridge.h"
#include "crt.h"
#include "generic.h"
#include "snapshot.h"
#include "types.h"
#include "util.h"

/*
    the default cartridge works like this:

    1 banking register (for ROM only)
    - 8k ROM banks
    - 8k RAM may be enabled at ROML

    The mappings of the carts supported are as follows:

    size   type   roml          romh
    ----   ----   ----          ----
     4k    8k     $8000-$8FFF(*)n/a             FIXME
     4k    ulti   n/a           $F000-$FFFF(*)  FIXME
     8k    8k     $8000-$9FFF   n/a
     8k    ulti   n/a           $E000-$FFFF
    12k    16k    $8000-$9FFF   $e000-$eFFF(*)  FIXME
    12k    ulti   $8000-$9FFF   $F000-$FFFF(*)  FIXME
    16k    16k    $8000-$9FFF   $A000-$BFFF
    16k    ulti   $8000-$9FFF   $E000-$FFFF

    *) actually mirrored over the whole 8k block.
*/

/* #define DBGGENERIC */

#ifdef DBGGENERIC
#define DBG(x) printf x
#else
#define DBG(x)
#endif

/* FIXME: these are shared between all "main slot" carts,
          individual cart implementations should get reworked to use local buffers */
/* Expansion port ROML/ROMH images.  */
BYTE roml_banks[C64CART_ROM_LIMIT], romh_banks[C64CART_ROM_LIMIT];
/* Expansion port RAM images.  */
BYTE export_ram0[C64CART_RAM_LIMIT];
/* Expansion port ROML/ROMH/RAM banking.  */
int roml_bank = 0, romh_bank = 0, export_ram = 0;

/* ---------------------------------------------------------------------*/

static const c64export_resource_t export_res_8kb = {
    "Generic 8KB", 1, 0, NULL, NULL, CARTRIDGE_GENERIC_8KB
};

static const c64export_resource_t export_res_16kb = {
    "Generic 16KB", 1, 1, NULL, NULL, CARTRIDGE_GENERIC_16KB
};

static c64export_resource_t export_res_ultimax = {
    "Generic Ultimax", 0, 1, NULL, NULL, CARTRIDGE_ULTIMAX
};

/* ---------------------------------------------------------------------*/

void generic_8kb_config_init(void)
{
    cart_config_changed_slotmain(0, 0, CMODE_READ);
}

void generic_16kb_config_init(void)
{
    cart_config_changed_slotmain(1, 1, CMODE_READ);
}

void generic_ultimax_config_init(void)
{
    cart_config_changed_slotmain(3, 3, CMODE_READ);
}

void generic_8kb_config_setup(BYTE *rawcart)
{
    memcpy(roml_banks, rawcart, 0x2000);
    cart_config_changed_slotmain(0, 0, CMODE_READ);
}

void generic_16kb_config_setup(BYTE *rawcart)
{
    memcpy(roml_banks, rawcart, 0x2000);
    memcpy(romh_banks, &rawcart[0x2000], 0x2000);
    cart_config_changed_slotmain(1, 1, CMODE_READ);
}

void generic_ultimax_config_setup(BYTE *rawcart)
{
    memcpy(&roml_banks[0x0000], &rawcart[0x0000], 0x2000);
    memcpy(&romh_banks[0x0000], &rawcart[0x2000], 0x2000);
    cart_config_changed_slotmain(3, 3, CMODE_READ);
}

int generic_common_attach(int mode)
{
    switch (mode) {
        case CARTRIDGE_GENERIC_8KB:
            DBG(("generic: attach 8kb\n"));
            if (c64export_add(&export_res_8kb) < 0) {
                return -1;
            }
            break;
        case CARTRIDGE_GENERIC_16KB:
            DBG(("generic: attach 16kb\n"));
            if (c64export_add(&export_res_16kb) < 0) {
                return -1;
            }
            break;
        case CARTRIDGE_ULTIMAX:
            DBG(("generic: attach ultimax\n"));
            if (c64export_add(&export_res_ultimax) < 0) {
                return -1;
            }
            break;
    }
    return 0;
}

int generic_8kb_bin_attach(const char *filename, BYTE *rawcart)
{
    if (util_file_load(filename, rawcart, 0x2000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
        /* also accept 4k binaries */
        if (util_file_load(filename, rawcart, 0x1000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
            return -1;
        }
        memcpy(&rawcart[0x1000], rawcart, 0x1000);
    }
    return generic_common_attach(CARTRIDGE_GENERIC_8KB);
}

int generic_16kb_bin_attach(const char *filename, BYTE *rawcart)
{
    if (util_file_load(filename, rawcart, 0x4000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
        /* also accept 12k binaries */
        if (util_file_load(filename, rawcart, 0x3000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
            return -1;
        }
        memcpy(&rawcart[0x3000], &rawcart[0x2000], 0x1000);
    }
    return generic_common_attach(CARTRIDGE_GENERIC_16KB);
}

int generic_ultimax_bin_attach(const char *filename, BYTE *rawcart)
{
    if (util_file_load(filename, rawcart, 0x4000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
        /* also accept 12k binaries */
        if (util_file_load(filename, rawcart, 0x3000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
            /* also accept 4k binaries */
            if (util_file_load(filename, &rawcart[0x2000], 0x1000, UTIL_FILE_LOAD_SKIP_ADDRESS) < 0) {
                return -1;
            }
        }
        memcpy(&rawcart[0x3000], &rawcart[0x2000], 0x1000);
    }
    return generic_common_attach(CARTRIDGE_ULTIMAX);
}

/*
    returns -1 on error, else a positive CRT ID
*/
int generic_crt_attach(FILE *fd, BYTE *rawcart)
{
    BYTE chipheader[0x10];
    int crttype = -1;

    export_res_ultimax.game = 0;

    if (fread(chipheader, 0x10, 1, fd) < 1) {
        return -1;
    }

    DBG(("chip1 at %02x len %02x\n", chipheader[0xc], chipheader[0xe]));
    if (chipheader[0xc] == 0x80 && chipheader[0xe] != 0 && chipheader[0xe] <= 0x40) {
        if (fread(rawcart, chipheader[0xe] << 8, 1, fd) < 1) {
            return -1;
        }
        crttype = (chipheader[0xe] <= 0x20) ? CARTRIDGE_GENERIC_8KB : CARTRIDGE_GENERIC_16KB;
        DBG(("type %d\n", crttype));
        /* try to read next CHIP header in case of 16k Ultimax cart */
        if (fread(chipheader, 0x10, 1, fd) < 1) {
            DBG(("type %d (generic game)\n", crttype));
            if (generic_common_attach(crttype) < 0) {
                return -1;
            }
            return crttype;
        } else {
            export_res_ultimax.game = 1;
        }
        DBG(("chip2 at %02x len %02x\n", chipheader[0xc], chipheader[0xe]));
    }

    if (chipheader[0xc] >= 0xe0 && chipheader[0xe] != 0 && (chipheader[0xe] + chipheader[0xc]) == 0x100) {
        if (fread(rawcart + ((chipheader[0xc] << 8) & 0x3fff), chipheader[0xe] << 8, 1, fd) < 1) {
            return -1;
        }
        if (generic_common_attach(CARTRIDGE_ULTIMAX) < 0) {
            return -1;
        }
        return CARTRIDGE_ULTIMAX;
    }

    return -1;
}

void generic_8kb_detach(void)
{
    DBG(("generic: detach 8kb\n"));
    c64export_remove(&export_res_8kb);
}

void generic_16kb_detach(void)
{
    DBG(("generic: detach 16kb\n"));
    c64export_remove(&export_res_16kb);
}

void generic_ultimax_detach(void)
{
    DBG(("generic: detach ultimax\n"));
    c64export_remove(&export_res_ultimax);
}

/* ---------------------------------------------------------------------*/

/* ROML read - mapped to 8000 in 8k,16k,ultimax */
BYTE REGPARM1 generic_roml_read(WORD addr)
{
    if (export_ram) {
        return export_ram0[addr & 0x1fff];
    }

    return roml_banks[(addr & 0x1fff) + (roml_bank << 13)];
}

/* ROML store - mapped to 8000 in ultimax mode */
void REGPARM2 generic_roml_store(WORD addr, BYTE value)
{
    if (export_ram) {
        export_ram0[addr & 0x1fff] = value;
    }
}

/* ROMH read - mapped to A000 in 16k, to E000 in ultimax */
BYTE REGPARM1 generic_romh_read(WORD addr)
{
    return romh_banks[(addr & 0x1fff) + (romh_bank << 13)];
}

BYTE REGPARM1 generic_romh_phi1_read(WORD addr)
{
    return romh_banks[(romh_bank << 13) + (addr & 0x1fff)];
}

BYTE REGPARM1 generic_romh_phi2_read(WORD addr)
{
    return romh_banks[(romh_bank << 13) + (addr & 0x1fff)];
}

BYTE *generic_romh_phi1_ptr(WORD addr)
{
    return romh_banks + (romh_bank << 13) + (addr & 0x1fff);
}

BYTE *generic_romh_phi2_ptr(WORD addr)
{
    return romh_banks + (romh_bank << 13) + (addr & 0x1fff);
}

BYTE generic_peek_mem(WORD addr)
{
    if (addr >= 0x8000 && addr <= 0x9fff) {
        if (export_ram) {
            return export_ram0[addr & 0x1fff];
        }
        return roml_banks[(addr & 0x1fff) + (roml_bank << 13)];
    }

    /* FIXME: export.xxx */
    if (!export.exrom && export.game) {
        if (addr >= 0xe000 && addr <= 0xffff) {
            return romh_banks[(addr & 0x1fff) + (romh_bank << 13)];
        }
    } else {
        if (addr >= 0xa000 && addr <= 0xbfff) {
            return romh_banks[(addr & 0x1fff) + (romh_bank << 13)];
        }
    }

    return ram_read(addr);
}

/* ---------------------------------------------------------------------*/

#define CART_DUMP_VER_MAJOR   0
#define CART_DUMP_VER_MINOR   0
#define SNAP_MODULE_NAME  "CARTGENERIC"

int generic_snapshot_write_module(snapshot_t *s, int type)
{
    snapshot_module_t *m;

    m = snapshot_module_create(s, SNAP_MODULE_NAME,
                          CART_DUMP_VER_MAJOR, CART_DUMP_VER_MINOR);
    if (m == NULL) {
        return -1;
    }

    if (0
        || (SMW_BA(m, roml_banks, 0x2000) < 0)
        || ((type != CARTRIDGE_GENERIC_8KB) && (SMW_BA(m, romh_banks, 0x2000) < 0))) {
        snapshot_module_close(m);
        return -1;
    }

    snapshot_module_close(m);
    return 0;
}

int generic_snapshot_read_module(snapshot_t *s, int type)
{
    BYTE vmajor, vminor;
    snapshot_module_t *m;

    m = snapshot_module_open(s, SNAP_MODULE_NAME, &vmajor, &vminor);
    if (m == NULL) {
        return -1;
    }

    if ((vmajor != CART_DUMP_VER_MAJOR) || (vminor != CART_DUMP_VER_MINOR)) {
        snapshot_module_close(m);
        return -1;
    }

    if (0
        || (SMR_BA(m, roml_banks, 0x2000) < 0)
        || ((type != CARTRIDGE_GENERIC_8KB) && (SMR_BA(m, romh_banks, 0x2000) < 0))) {
        snapshot_module_close(m);
        return -1;
    }

    snapshot_module_close(m);

    switch (type) {
        case CARTRIDGE_GENERIC_8KB:
            return c64export_add(&export_res_8kb);
        case CARTRIDGE_GENERIC_16KB:
            return c64export_add(&export_res_16kb);
        case CARTRIDGE_ULTIMAX:
            return c64export_add(&export_res_ultimax);
    }

    return -1;
}