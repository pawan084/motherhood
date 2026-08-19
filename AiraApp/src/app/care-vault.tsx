import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/icon';

const THIS_TRIMESTER: VaultDocument[] = [
  {
    title: 'Anomaly scan report',
    meta: 'PDF · 2.1 MB · 20 Aug',
    icon: 'crosshair',
  },
  {
    title: 'Blood work — full panel',
    meta: 'PDF · 640 KB · 14 Aug',
    icon: 'crosshair',
  },
  {
    title: 'Prenatal vitamin — prescription',
    meta: 'JPG · 1.4 MB · 2 Aug',
    icon: 'paperclip',
  },
];

const EARLIER: VaultDocument[] = [
  {
    title: 'Dating scan report',
    meta: 'PDF · 1.8 MB · 3 Jul',
    icon: 'crosshair',
  },
];

type VaultDocument = {
  title: string;
  meta: string;
  icon: IconName;
};

function Header() {
  return (
    <View className="border-b border-border bg-card px-5 pb-4 pt-2">
      <View className="flex-row items-center gap-4">
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
          className="active:opacity-70">
          <View className="h-11 w-11 items-center justify-center rounded-full border border-border bg-card">
            <Icon name="arrow-left" size={22} className="text-foreground" />
          </View>
        </Pressable>
        <Text className="font-serif text-2xl text-foreground">Care vault</Text>
      </View>
    </View>
  );
}

function DocumentRow({ doc, last }: { doc: VaultDocument; last?: boolean }) {
  return (
    <View>
      <View className="flex-row items-center gap-3 py-3">
        <Icon name={doc.icon} size={20} className="text-foreground" />
        <View className="flex-1">
          <Text className="font-rubik-semibold text-base leading-tight text-foreground">
            {doc.title}
          </Text>
          <Text className="mt-0.5 font-rubik text-xs text-muted-foreground">{doc.meta}</Text>
        </View>
        <Pressable
          onPress={() => router.push('/care-vault-share')}
          accessibilityRole="button"
          accessibilityLabel={`Share ${doc.title}`}
          hitSlop={10}>
          <Icon name="external-link" size={19} className="text-muted-foreground" />
        </Pressable>
      </View>
      {!last && <View className="ml-8 h-px bg-border" />}
    </View>
  );
}

export default function CareVaultScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="gap-4 px-5 pb-8 pt-0"
        showsVerticalScrollIndicator={false}>
        <View
          className="mx-2 flex-row items-center gap-3 rounded-b-[20px] bg-[#EFFBF3] px-5 py-4"
          style={{
            shadowColor: '#7527F5',
            shadowOpacity: 0.08,
            shadowRadius: 12,
            shadowOffset: { width: 0, height: 7 },
            elevation: 3,
          }}>
          <Icon name="lock" size={22} className="text-foreground" />
          <View className="flex-1">
            <Text className="font-rubik-semibold text-base text-foreground">Private to you</Text>
            <Text className="font-rubik text-xs text-muted-foreground">
              Stored on your device · never used for ads
            </Text>
          </View>
        </View>

        <View className="mt-2 flex-row items-center justify-between">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            This trimester (3)
          </Text>
          <Pressable accessibilityRole="button" hitSlop={8} className="active:opacity-70">
            <Text className="font-rubik-semibold text-sm text-brand">＋ Add</Text>
          </Pressable>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-4 py-2">
          {THIS_TRIMESTER.map((doc, index) => (
            <DocumentRow
              key={doc.title}
              doc={doc}
              last={index === THIS_TRIMESTER.length - 1}
            />
          ))}
        </View>

        <Text className="mt-1 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
          Earlier
        </Text>

        <View className="rounded-[20px] border border-border bg-card px-4 py-2">
          {EARLIER.map((doc, index) => (
            <DocumentRow key={doc.title} doc={doc} last={index === EARLIER.length - 1} />
          ))}
        </View>

        <Pressable accessibilityRole="button" className="active:opacity-80">
          <View className="flex-row items-center justify-between rounded-[20px] border border-border bg-secondary px-5 py-5">
            <View className="flex-1">
              <Text className="font-rubik-semibold text-base text-foreground">
                Access & active shares
              </Text>
              <Text className="mt-0.5 font-rubik text-xs leading-snug text-muted-foreground">
                No active share links · last opened by you today
              </Text>
            </View>
            <Icon name="chevron-right" size={22} className="text-brand" />
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.push('/care-vault-upload')}
          accessibilityRole="button"
          className="active:opacity-90">
          <LinearGradient
            colors={['#9333FF', '#6D17EA']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={{
              minHeight: 52,
              borderRadius: 16,
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              paddingHorizontal: 20,
              paddingVertical: 14,
              shadowColor: '#7527F5',
              shadowOpacity: 0.28,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <Icon name="upload" size={18} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Upload document
            </Text>
          </LinearGradient>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}
