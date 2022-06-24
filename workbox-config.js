module.exports = {
	globDirectory: 'src/',
	globPatterns: [
		'**/*.{css,tsx,svg,ts}'
	],
	swDest: 'src/sw.js',
	ignoreURLParametersMatching: [
		/^utm_/,
		/^fbclid$/
	]
};