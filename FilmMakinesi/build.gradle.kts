version = 41

cloudstream {
    authors     = listOf("fsmnk")
    language    = "tr"
    description = "Film Makinesi - En yeni ve en güncel filmler"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=filmmakinesi.to&sz=%size%"
}
