GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('trichlorosilane')
        .liquid(new GTFluidBuilder().state(GTFluidState.LIQUID).customStill())
        .color(0xfc037b).iconSet(GTMaterialIconSet.DULL)
        .element(GTElements.get('trichlorosilane'))
    event.create('hydrogensilesquioxane')
        .liquid(new GTFluidBuilder().state(GTFluidState.LIQUID).customStill())
        .color(0xc7003f).iconSet(GTMaterialIconSet.DULL)
        .element(GTElements.get('hydrogensilesquioxane'))
})