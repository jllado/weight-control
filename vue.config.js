module.exports = {
    devServer: {
        allowedHosts: 'all'
    },
    pwa: {
        name: 'Weight Control',
        themeColor: '#0476F2',
        msTileColor: '#000000',
        appleMobileWebAppCapable: 'yes',
        appleMobileWebAppStatusBarStyle: 'default',
        workboxPluginMode: 'InjectManifest',
        workboxOptions: {
            swSrc: './src/service-worker.js'
        },
        iconPaths: {
            faviconSVG: null,
            favicon32: 'favicon-32x32.png',
            favicon16: 'favicon-16x16.png',
            appleTouchIcon: 'apple-touch-icon.png',
            maskIcon: 'safari-pinned-tab.svg',
            msTileImage: 'mstile-150x150.png'
        },
        manifestOptions: {
            id: '/',
            short_name: 'WC',
            start_url: '/',
            scope: '/',
            display: 'standalone',
            background_color: '#ffffff',
            description: 'Track weight, blood pressure, habits, and routines.',
            icons: [
                {
                    src: '/android-chrome-192x192.png',
                    sizes: '192x192',
                    type: 'image/png'
                },
                {
                    src: '/android-chrome-512x512.png',
                    sizes: '512x512',
                    type: 'image/png'
                }
            ]
        }
    }
};
