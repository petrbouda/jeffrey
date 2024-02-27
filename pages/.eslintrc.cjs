module.exports = {
    root: true,
    env: {
        node: true
    },
    extends: ['eslint:recommended', 'plugin:vue/vue3-essential', 'prettier'],
    parserOptions: {
        parser: '@babel/eslint-parser',
        requireConfigFile: false
    },
    plugins: ['prettier'],
    ignorePatterns: ['**/public/**', '**/dist/**'],
    rules: {
        'vue/multi-word-component-names': 'off',
        'vue/no-reserved-component-names': 'off'
    }
};
