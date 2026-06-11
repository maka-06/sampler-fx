#ifndef SAMPLEAPP_WAVIO_H
#define SAMPLEAPP_WAVIO_H

#include <cstdio>
#include <cstdint>
#include <cstring>
#include <vector>

// Lecteur WAV minimal pour nos propres fichiers (PCM 16-bit). Renvoie un signal
// mono float ; si le fichier est stéréo, les canaux sont moyennés.
// Le FILE* est fermé par la fonction. Renvoie un vecteur vide en cas d'échec.
namespace WavIo {

inline uint32_t readU32(const uint8_t* p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}
inline uint16_t readU16(const uint8_t* p) {
    return (uint16_t) ((uint16_t) p[0] | ((uint16_t) p[1] << 8));
}

inline std::vector<float> readWavMono16(FILE* f) {
    std::vector<float> out;
    if (!f) return out;

    uint8_t header[12];
    if (fread(header, 1, 12, f) != 12 ||
        std::memcmp(header, "RIFF", 4) != 0 ||
        std::memcmp(header + 8, "WAVE", 4) != 0) {
        fclose(f);
        return out;
    }

    uint16_t channels = 1;
    uint16_t bitsPerSample = 16;
    bool haveFmt = false;

    // Parcours des chunks
    uint8_t chunkHdr[8];
    while (fread(chunkHdr, 1, 8, f) == 8) {
        uint32_t chunkSize = readU32(chunkHdr + 4);
        if (std::memcmp(chunkHdr, "fmt ", 4) == 0) {
            std::vector<uint8_t> fmt(chunkSize);
            if (fread(fmt.data(), 1, chunkSize, f) != chunkSize) break;
            if (chunkSize >= 16) {
                channels = readU16(fmt.data() + 2);
                bitsPerSample = readU16(fmt.data() + 14);
            }
            haveFmt = true;
            if (chunkSize % 2 == 1) fseek(f, 1, SEEK_CUR); // padding
        } else if (std::memcmp(chunkHdr, "data", 4) == 0) {
            if (!haveFmt || bitsPerSample != 16 || channels < 1) { break; }
            uint32_t numSamples16 = chunkSize / 2; // échantillons 16-bit bruts
            std::vector<int16_t> raw(numSamples16);
            size_t got = fread(raw.data(), sizeof(int16_t), numSamples16, f);
            int frames = (int) (got / channels);
            out.resize(frames);
            for (int i = 0; i < frames; ++i) {
                int acc = 0;
                for (int c = 0; c < channels; ++c) acc += raw[i * channels + c];
                float v = (float) acc / (float) channels;
                out[i] = v / 32768.0f;
            }
            break; // on s'arrête après le chunk data
        } else {
            // chunk inconnu : on saute (avec padding éventuel)
            fseek(f, chunkSize + (chunkSize % 2), SEEK_CUR);
        }
    }

    fclose(f);
    return out;
}

} // namespace WavIo

#endif // SAMPLEAPP_WAVIO_H
