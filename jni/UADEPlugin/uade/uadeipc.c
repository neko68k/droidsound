/* UADE
 *
 * Copyright 2005 Heikki Orsila <heikki.orsila@iki.fi>
 *
 * This source code module is dual licensed under GPL and Public Domain.
 * Hence you may use _this_ module (not another code module) in any way you
 * want in your projects.
 */

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <assert.h>
#include <string.h>
#include <netinet/in.h>

#include <uadeipc.h>
#include <strlrep.h>
#include <ipcsupport.h>

#include <android/log.h>

static int valid_message(struct uade_msg *uc);


void uade_check_fix_string(struct uade_msg *um, size_t maxlen)
{
  uint8_t *s = (uint8_t *) um->data;
  size_t safelen;
  if (um->size == 0) {
    s[0] = 0;
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "zero string detected\n");
  }
  safelen = 0;
  while (s[safelen] != 0 && safelen < maxlen)
    safelen++;
  if (safelen == maxlen) {
    safelen--;
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "too long a string\n");
    s[safelen] = 0;
  }
  if (um->size != (safelen + 1)) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "string size does not match\n");
    um->size = safelen + 1;
    s[safelen] = 0;
  }
}


static ssize_t get_more(size_t bytes, struct uade_ipc *ipc)
{
  if (ipc->inputbytes < bytes) {
    ssize_t s = uade_ipc_read(ipc->input, &ipc->inputbuffer[ipc->inputbytes], bytes - ipc->inputbytes);
    if (s <= 0)
      return -1;
    ipc->inputbytes += s;
  }
  return 0;
}


static void copy_from_inputbuffer(void *dst, int bytes, struct uade_ipc *ipc)
{
  if (ipc->inputbytes < bytes) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "not enough bytes in input buffer\n");
    exit(-1);
  }
  memcpy(dst, ipc->inputbuffer, bytes);
  memmove(ipc->inputbuffer, &ipc->inputbuffer[bytes], ipc->inputbytes - bytes);
  ipc->inputbytes -= bytes;
}


int uade_receive_message(struct uade_msg *um, size_t maxbytes, struct uade_ipc *ipc)
{
  size_t fullsize;

  //__android_log_print(ANDROID_LOG_VERBOSE, "uadecontrol", "STATE %d", ipc->state);

  if (ipc->state == UADE_INITIAL_STATE) {
    ipc->state = UADE_R_STATE;
  } else if (ipc->state == UADE_S_STATE) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "protocol error: receiving in S state is forbidden\n");
    return -1;
  }

  if (ipc->inputbytes < sizeof(*um)) {
    if (get_more(sizeof(*um), ipc))
      return 0;
  }

  copy_from_inputbuffer(um, sizeof(*um), ipc);

  um->msgtype = ntohl(um->msgtype);
  um->size = ntohl(um->size);

if (!valid_message(um))
    return -1;

  fullsize = um->size + sizeof(*um);
  if (fullsize > maxbytes) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "too big a command: %zu\n", fullsize);
    return -1;
  }
  if (ipc->inputbytes < um->size) {
    if (get_more(um->size, ipc))
      return -1;
  }
  copy_from_inputbuffer(&um->data, um->size, ipc);

  if (um->msgtype == UADE_COMMAND_TOKEN)
    ipc->state = UADE_S_STATE;

  return 1;
}


int uade_receive_short_message(enum uade_msgtype msgtype, struct uade_ipc *ipc)
{
  struct uade_msg um;

  if (ipc->state == UADE_INITIAL_STATE) {
    ipc->state = UADE_R_STATE;
  } else if (ipc->state == UADE_S_STATE) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "protocol error: receiving (%d) in S state is forbidden\n", msgtype);
    return -1;
  }

  if (uade_receive_message(&um, sizeof(um), ipc) <= 0) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "can not receive short message: %d\n", msgtype);
    return -1;
  }
  return (um.msgtype == msgtype) ? 0 : -1;
}


int uade_receive_string(char *s, enum uade_msgtype com,
			size_t maxlen, struct uade_ipc *ipc)
{
  const size_t COMLEN = 4096;
  uint8_t commandbuf[COMLEN];
  struct uade_msg *um = (struct uade_msg *) commandbuf;
  int ret;

  if (ipc->state == UADE_INITIAL_STATE) {
    ipc->state = UADE_R_STATE;
  } else if (ipc->state == UADE_S_STATE) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "protocol error: receiving in S state is forbidden\n");
    return -1;
  }

  ret = uade_receive_message(um, COMLEN, ipc);
  if (ret <= 0)
    return ret;
  if (um->msgtype != com)
    return -1;
  if (um->size == 0)
    return -1;
  if (um->size != (strlen((char *) um->data) + 1))
    return -1;
  strlcpy(s, (char *) um->data, maxlen);
  return 1;
}


int uade_send_message(struct uade_msg *um, struct uade_ipc *ipc)
{
  uint32_t size = um->size;

  if (ipc->state == UADE_INITIAL_STATE) {
    ipc->state = UADE_S_STATE;
  } else if (ipc->state == UADE_R_STATE) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "protocol error: sending in R state is forbidden\n");
    return -1;
  }

  if (!valid_message(um))
    return -1;
  if (um->msgtype == UADE_COMMAND_TOKEN)
    ipc->state = UADE_R_STATE;
  um->msgtype = htonl(um->msgtype);
  um->size = htonl(um->size);
  if (uade_ipc_write(ipc->output, um, sizeof(*um) + size) < 0)
    return -1;

  return 0;
}


int uade_send_short_message(enum uade_msgtype msgtype, struct uade_ipc *ipc)
{
  if (uade_send_message(& (struct uade_msg) {.msgtype = msgtype}, ipc)) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "can not send short message: %d\n", msgtype);
    return -1;
  }
  return 0;
}


int uade_send_string(enum uade_msgtype com, const char *str, struct uade_ipc *ipc)
{
  uint32_t size = strlen(str) + 1;
  struct uade_msg um = {.msgtype = ntohl(com), .size = ntohl(size)};

  if (ipc->state == UADE_INITIAL_STATE) {
    ipc->state = UADE_S_STATE;
  } else if (ipc->state == UADE_R_STATE) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "protocol error: sending in R state is forbidden\n");
    return -1;
  }

  if ((sizeof(um) + size) > UADE_MAX_MESSAGE_SIZE)
    return -1;
  if (uade_ipc_write(ipc->output, &um, sizeof(um)) < 0)
    return -1;
  if (uade_ipc_write(ipc->output, str, size) < 0)
    return -1;

  return 0;
}


void uade_set_peer(struct uade_ipc *ipc, int peer_is_client, const char *input, const char *output)
{
  assert(peer_is_client == 0 || peer_is_client == 1);
  assert(input != NULL);
  assert(output != NULL);

  *ipc = (struct uade_ipc) {.state = UADE_INITIAL_STATE,
			    .input= uade_ipc_set_input(input),
			    .output = uade_ipc_set_output(output)};
}


static int valid_message(struct uade_msg *um)
{
  size_t len;
  if (um->msgtype <= UADE_MSG_FIRST || um->msgtype >= UADE_MSG_LAST) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "unknown command: %d\n", um->msgtype);
    return 0;
  }
  len = sizeof(*um) + um->size;
  if (len > UADE_MAX_MESSAGE_SIZE) {
    __android_log_print(ANDROID_LOG_VERBOSE, "uadeipc", "too long a message: %zd\n", len);
    return 0;
  }
  return 1;
}
