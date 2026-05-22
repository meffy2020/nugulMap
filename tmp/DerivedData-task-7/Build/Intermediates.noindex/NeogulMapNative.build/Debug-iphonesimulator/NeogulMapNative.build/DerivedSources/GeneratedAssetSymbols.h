#import <Foundation/Foundation.h>

#if __has_attribute(swift_private)
#define AC_SWIFT_PRIVATE __attribute__((swift_private))
#else
#define AC_SWIFT_PRIVATE
#endif

/// The resource bundle ID.
static NSString * const ACBundleID AC_SWIFT_PRIVATE = @"com.nugulmap.native";

/// The "AccentColor" asset catalog color resource.
static NSString * const ACColorNameAccentColor AC_SWIFT_PRIVATE = @"AccentColor";

/// The "NugulMarker" asset catalog image resource.
static NSString * const ACImageNameNugulMarker AC_SWIFT_PRIVATE = @"NugulMarker";

#undef AC_SWIFT_PRIVATE
