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
