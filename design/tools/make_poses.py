# -*- coding: utf-8 -*-
"""오또 자세 그림 9장을 ComfyUI(FLUX.1 Kontext GGUF)로 만든다.

지금 오또 그림(assets/마스코트.png)을 흰 바탕 1024px로 넣고, 자세만 바꾸라고 지시한 뒤
BiRefNet 으로 배경을 투명하게 따서 저장한다.

준비: ComfyUI 가 127.0.0.1:8188 에 떠 있어야 하고, 다음 파일이 있어야 한다.
  models/unet/flux1-kontext-dev-Q4_K_M.gguf  (custom_nodes/ComfyUI-GGUF)
  models/text_encoders/clip_l.safetensors · t5xxl_fp8_e4m3fn_scaled.safetensors
  models/vae/ae.safetensors · models/background_removal/birefnet.safetensors
  input/otto_ref.png  (흰 바탕 오또)

    python design/tools/make_poses.py            # 9장 모두
    python design/tools/make_poses.py 하품 걷기    # 골라서
"""
import json
import random
import sys
import urllib.request

URL = "http://127.0.0.1:8188"

KEEP = ("Keep the exact same kitten character: orange and cream fluffy fur, teal-green felt hood with cat ears "
        "and blue trim, round yellow gold button, pink paw pads, big glossy eyes. Same soft plush 3D render style "
        "and lighting. Full body, centered, plain pure white background.")

POSES = {
    "인사": "Make the kitten wave one paw high in the air saying hello, big happy smile.",
    "폰 내밀기": "Make the kitten hold a small smartphone forward with both paws, offering it to a grown-up, "
                 "friendly smile.",
    "가리키기": "Make the kitten point to the right side with one paw, head turned toward where it points, excited.",
    "걷기": "Make the kitten walk toward the right in a cheerful mid-step, three-quarter view, one foot lifted.",
    "말하기": "Make the kitten talk happily with its mouth open, one paw raised in an expressive gesture.",
    "귀 쫑긋": "Edit only the arm and head: the kitten brings its right paw up to touch the side of its own head right "
              "next to its ear, palm facing forward like listening to a whisper, head tilted toward that paw, mouth "
              "closed, eyes looking sideways attentively.",
    "갸웃": "Change the pose and expression: the kitten tilts its whole head strongly to one side, rests one paw "
            "under its chin, looks up thoughtfully with a small closed mouth, puzzled.",
    "하품": "Change the expression: the kitten is very sleepy, droopy half-closed eyes, mouth stretched wide open "
            "in a big yawn, one paw rubbing its eye.",
    "어른 부르기": "Edit only the arms and mouth: both paws are pressed against the kitten's cheeks on each side of its "
              "wide open mouth, shouting loudly, eyes squeezed with effort.",
}
FILE = {"인사": "wave", "폰 내밀기": "phone", "가리키기": "point", "걷기": "walk", "말하기": "talk",
        "귀 쫑긋": "listen", "갸웃": "think", "하품": "yawn", "어른 부르기": "call"}


def workflow(instruction, prefix, seed):
    return {
        "1": {"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": "flux1-kontext-dev-Q4_K_M.gguf"}},
        "2": {"class_type": "DualCLIPLoader", "inputs": {"clip_name1": "clip_l.safetensors",
                                                          "clip_name2": "t5xxl_fp8_e4m3fn_scaled.safetensors",
                                                          "type": "flux"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "ae.safetensors"}},
        "4": {"class_type": "LoadImage", "inputs": {"image": "otto_ref.png"}},
        "5": {"class_type": "FluxKontextImageScale", "inputs": {"image": ["4", 0]}},
        "6": {"class_type": "VAEEncode", "inputs": {"pixels": ["5", 0], "vae": ["3", 0]}},
        "7": {"class_type": "CLIPTextEncode", "inputs": {"text": f"{instruction} {KEEP}", "clip": ["2", 0]}},
        "8": {"class_type": "ReferenceLatent", "inputs": {"conditioning": ["7", 0], "latent": ["6", 0]}},
        "9": {"class_type": "FluxGuidance", "inputs": {"conditioning": ["8", 0], "guidance": 2.5}},
        "10": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["7", 0]}},
        "11": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["9", 0], "negative": ["10", 0],
                                                    "latent_image": ["6", 0], "seed": seed, "steps": 20, "cfg": 1.0,
                                                    "sampler_name": "euler", "scheduler": "simple", "denoise": 1.0}},
        "12": {"class_type": "VAEDecode", "inputs": {"samples": ["11", 0], "vae": ["3", 0]}},
        "13": {"class_type": "LoadBackgroundRemovalModel", "inputs": {"bg_removal_name": "birefnet.safetensors"}},
        "14": {"class_type": "RemoveBackground", "inputs": {"bg_removal_model": ["13", 0], "image": ["12", 0]}},
        "15": {"class_type": "InvertMask", "inputs": {"mask": ["14", 0]}},
        "16": {"class_type": "JoinImageWithAlpha", "inputs": {"image": ["12", 0], "alpha": ["15", 0]}},
        "17": {"class_type": "SaveImage", "inputs": {"images": ["16", 0], "filename_prefix": prefix}},
    }


def enqueue(wf):
    req = urllib.request.Request(URL + "/prompt", data=json.dumps({"prompt": wf}).encode(),
                                 headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req))["prompt_id"]


if __name__ == "__main__":
    names = sys.argv[1:] or list(POSES)
    for n in names:
        pid = enqueue(workflow(POSES[n], f"otto_pose/{FILE[n]}", random.randint(1, 2 ** 31)))
        print(n, FILE[n], pid)
