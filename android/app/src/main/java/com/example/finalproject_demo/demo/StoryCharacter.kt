package com.example.finalproject_demo.demo

import com.example.finalproject_demo.ui.HeroAttr

fun Hero.storyArt(): Art = image?.let { Art.Img(it, Art.HeroArt(attr), rig) } ?: Art.HeroArt(attr)

val DemoState.storyHeroArt: Art
    get() = storyHeroImage?.let { Art.Img(it, Art.HeroArt(heroAttr ?: HeroAttr()), storyHeroRig) }
        ?: Art.HeroArt(heroAttr ?: HeroAttr())
