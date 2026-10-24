// The demo pack: its assets folder holds demo/ (assetdropkit.json, image.png, hello.txt).
// The iOS sample tags the same folder for On-Demand Resources and archives it for Apple hosting.
plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("demo")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
