const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function (app) {
    // Proxy /transport.php requests to nginx on port 80
    app.use(
        '/transport.php',
        createProxyMiddleware({
            target: 'http://localhost',
            changeOrigin: true,
        })
    );
};
