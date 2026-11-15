#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#ifndef TIOCSPTLCK
#define TIOCSPTLCK _IOW('T', 0x31, int)
#endif
#ifndef TIOCGPTN
#define TIOCGPTN _IOR('T', 0x30, unsigned int)
#endif
#ifndef TIOCSWINSZ
#define TIOCSWINSZ _IOW('T', 0x67, struct winsize)
#endif

static char **to_array(JNIEnv *env, jobjectArray arr) {
    if (arr == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, arr);
    char **out = (char **) calloc((size_t) n + 1, sizeof(char *));
    if (out == NULL) return NULL;
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, arr, i);
        const char *utf = (*env)->GetStringUTFChars(env, s, NULL);
        out[i] = strdup(utf != NULL ? utf : "");
        if (utf != NULL) (*env)->ReleaseStringUTFChars(env, s, utf);
        (*env)->DeleteLocalRef(env, s);
    }
    out[n] = NULL;
    return out;
}

static void free_array(char **arr) {
    if (arr == NULL) return;
    for (int i = 0; arr[i] != NULL; i++) free(arr[i]);
    free(arr);
}

JNIEXPORT jintArray JNICALL
Java_com_zrinfile_app_term_Pty_start(JNIEnv *env, jclass clazz,
                                     jstring cmd, jobjectArray args, jobjectArray envs,
                                     jstring cwd, jint cols, jint rows) {
    const char *cmd_utf = (*env)->GetStringUTFChars(env, cmd, NULL);
    char *cmd_copy = strdup(cmd_utf != NULL ? cmd_utf : "");
    if (cmd_utf != NULL) (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
    char **argv = to_array(env, args);
    char **envp = to_array(env, envs);
    const char *cwd_utf = cwd != NULL ? (*env)->GetStringUTFChars(env, cwd, NULL) : NULL;

    int master = open("/dev/ptmx", O_RDWR);
    if (master < 0) {
        free(cmd_copy);
        free_array(argv);
        free_array(envp);
        if (cwd_utf != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        return NULL;
    }

    int unlock = 0;
    if (ioctl(master, TIOCSPTLCK, &unlock) < 0) {
        close(master);
        free(cmd_copy);
        free_array(argv);
        free_array(envp);
        if (cwd_utf != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        return NULL;
    }

    unsigned int ptn = 0;
    if (ioctl(master, TIOCGPTN, &ptn) < 0) {
        close(master);
        free(cmd_copy);
        free_array(argv);
        free_array(envp);
        if (cwd_utf != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        return NULL;
    }

    char slave_path[64];
    snprintf(slave_path, sizeof(slave_path), "/dev/pts/%u", ptn);

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ioctl(master, TIOCSWINSZ, &ws);

    pid_t pid = fork();
    if (pid < 0) {
        close(master);
        free(cmd_copy);
        free_array(argv);
        free_array(envp);
        if (cwd_utf != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        return NULL;
    }

    if (pid == 0) {
        setsid();
        int slave = open(slave_path, O_RDWR);
        if (slave < 0) _exit(127);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(master);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);
        if (cwd_utf != NULL && cwd_utf[0] != '\0') {
            if (chdir(cwd_utf) != 0) {
                /* keep the default directory */
            }
        }
        execve(cmd_copy, argv, envp);
        _exit(127);
    }

    if (cwd_utf != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
    free(cmd_copy);
    free_array(argv);
    free_array(envp);

    jint values[2];
    values[0] = master;
    values[1] = (jint) pid;
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result != NULL) (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT void JNICALL
Java_com_zrinfile_app_term_Pty_resize(JNIEnv *env, jclass clazz, jint fd, jint cols, jint rows) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_zrinfile_app_term_Pty_killProcess(JNIEnv *env, jclass clazz, jint pid) {
    if (pid <= 0) return;
    kill((pid_t) pid, SIGKILL);
    kill(-(pid_t) pid, SIGKILL);
}

JNIEXPORT void JNICALL
Java_com_zrinfile_app_term_Pty_closeFd(JNIEnv *env, jclass clazz, jint fd) {
    if (fd >= 0) close(fd);
}
