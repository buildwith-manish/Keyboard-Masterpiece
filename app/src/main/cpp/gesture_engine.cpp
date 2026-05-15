#include <jni.h>
#include <cmath>
#include <string>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_keyboardmasterpiece_nativebridge_NativeGestureBridge_nativeClassify(JNIEnv* env, jobject, jfloatArray arr, jint count) {
    if (count < 2 || arr == nullptr) return env->NewStringUTF("");
    const int len = count * 2;
    std::vector<float> p(len);
    env->GetFloatArrayRegion(arr, 0, len, p.data());
    float minX = p[0], maxX = p[0], minY = p[1], maxY = p[1], length = 0.0f;
    for (int i = 1; i < count; ++i) {
        float x = p[i * 2], y = p[i * 2 + 1];
        minX = std::min(minX, x); maxX = std::max(maxX, x); minY = std::min(minY, y); maxY = std::max(maxY, y);
        float px = p[(i - 1) * 2], py = p[(i - 1) * 2 + 1];
        length += std::hypot(x - px, y - py);
    }
    const float dx = p[(count - 1) * 2] - p[0];
    const float dy = p[(count - 1) * 2 + 1] - p[1];
    const float w = maxX - minX, h = maxY - minY;
    std::string word;
    if (length < 90.0f) word = "";
    else if (std::fabs(dx) > std::fabs(dy) * 2.0f && dx > 150.0f) word = "the";
    else if (std::fabs(dx) > std::fabs(dy) * 2.0f && dx < -150.0f) word = "and";
    else if (dy < -160.0f && h > w * 0.8f) word = "you";
    else if (dy > 160.0f && h > w * 0.8f) word = "to";
    else if (w > 220.0f && h > 90.0f) word = "keyboard";
    else word = "hello";
    return env->NewStringUTF(word.c_str());
}
