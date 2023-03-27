package com.hubspot.chrome.devtools.codegen;

public class ClassPackageResolver {
  private String packageName;
  private String className;

  public ClassPackageResolver(String packageName, String className) {
    this.packageName = packageName;
    this.className = className;

    if (packageName.contains(".") && className.contains(".")) {
      this.packageName =
        packageName.substring(0, packageName.lastIndexOf(".") + 1) +
        className.substring(0, className.indexOf(".")).toLowerCase();
      this.className = className.substring(className.indexOf(".") + 1);
    }
  }

  public String getPackageName() {
    return packageName;
  }

  public String getClassName() {
    return className;
  }
}
