const { withProjectBuildGradle } = require("@expo/config-plugins");

module.exports = function nexgoPlugin(config) {
  return withProjectBuildGradle(config, (cfg) => {
    if (cfg.modResults.language === "groovy") {
      cfg.modResults.contents = cfg.modResults.contents.replace(
        /repositories\s*{/,
        `repositories {
            flatDir { dirs 'libs' } // Añadido por Nexgo Plugin`
      );
    }
    return cfg;
  });
};