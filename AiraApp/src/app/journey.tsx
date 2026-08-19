import { router } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const WEEKS = [21, 22, 23, 24, 25, 26, 27];

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
        <Text className="font-serif text-2xl text-foreground">Your journey</Text>
      </View>
    </View>
  );
}

function Node({
  label,
  active,
  done,
  locked,
}: {
  label: string;
  active?: boolean;
  done?: boolean;
  locked?: boolean;
}) {
  return (
    <View
      className={
        active || done
          ? 'h-[58px] w-[58px] items-center justify-center rounded-full border-4 border-card bg-[#38C878]'
          : 'h-[58px] w-[58px] items-center justify-center rounded-full border-4 border-card bg-card'
      }
      style={{
        shadowColor: active ? '#38C878' : done ? '#1FBFAD' : '#7527F5',
        shadowOpacity: active || done ? 0.18 : 0.08,
        shadowRadius: 12,
        shadowOffset: { width: 0, height: 8 },
        elevation: 5,
      }}>
      {done ? (
        <Icon name="check" size={26} className="text-white" />
      ) : locked ? (
        <Icon name="lock" size={21} className="text-muted-foreground" />
      ) : (
        <Text className="font-rubik-semibold text-2xl text-white">{label}</Text>
      )}
    </View>
  );
}

function Chapter({
  eyebrow,
  title,
  body,
  className = '',
  onPress,
}: {
  eyebrow: string;
  title: string;
  body: string;
  className?: string;
  onPress?: () => void;
}) {
  const content = (
    <View className={`rounded-[14px] border border-border bg-card px-4 py-3 ${className}`}>
      <Text className="text-center font-rubik-semibold text-[10px] uppercase text-brand">
        {eyebrow}
      </Text>
      <Text className="text-center font-rubik-semibold text-sm leading-tight text-foreground">
        {title}
      </Text>
      <Text className="mt-0.5 text-center font-rubik text-xs text-muted-foreground">
        {body}
      </Text>
    </View>
  );

  if (onPress) {
    return (
      <Pressable onPress={onPress} accessibilityRole="button" className="active:opacity-80">
        {content}
      </Pressable>
    );
  }

  return (
    content
  );
}

export default function JourneyScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="px-5 pb-8 pt-4"
        showsVerticalScrollIndicator={false}>
        <View className="flex-row items-center justify-between rounded-[20px] border border-border bg-card px-5 py-2">
          {WEEKS.map((week) =>
            week === 24 ? (
              <View
                key={week}
                className="h-[58px] w-[58px] items-center justify-center rounded-full bg-brand"
                style={{
                  shadowColor: '#7527F5',
                  shadowOpacity: 0.35,
                  shadowRadius: 18,
                  shadowOffset: { width: 0, height: 10 },
                  elevation: 8,
                }}>
                <Text className="font-rubik-semibold text-2xl leading-7 text-brand-foreground">
                  24
                </Text>
                <Text className="font-rubik-semibold text-[9px] text-brand-foreground">
                  weeks
                </Text>
              </View>
            ) : (
              <Text key={week} className="font-rubik text-sm text-muted-foreground">
                {week}
              </Text>
            ),
          )}
        </View>

        <Text className="mt-4 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
          Your path
        </Text>

        <View className="min-h-[620px]">
          <View className="absolute left-[96px] top-[70px] h-[16px] w-[70px] rotate-[-35deg] rounded-full border-t-8 border-dotted border-[#73D3C7]" />
          <View className="absolute left-[178px] top-[212px] h-[95px] w-[70px] rotate-[34deg] rounded-full border-r-8 border-dotted border-[#73D3C7]" />
          <View className="absolute left-[112px] top-[330px] h-[130px] w-[92px] rotate-[38deg] rounded-full border-l-8 border-dotted border-[#E8D8F7]" />
          <View className="absolute left-[116px] top-[502px] h-[120px] w-[130px] rotate-[42deg] rounded-full border-t-8 border-dotted border-[#E8D8F7]" />

          <View className="absolute left-[136px] top-5">
            <Node label="24" active />
          </View>
          <Chapter
            eyebrow="Current chapter"
            title="Movement &"
            body="growth"
            className="absolute left-[88px] top-[86px] w-[170px]"
          />
          <View className="absolute left-[16px] top-[140px]">
            <Node label="" done />
          </View>
          <Chapter
            eyebrow="Completed"
            title="Movement patterns"
            body="What can feel typical now"
            className="absolute left-[76px] top-[150px] w-[160px]"
          />

          <View
            className="absolute right-[4px] top-[235px] h-[76px] w-[76px] items-center justify-center rounded-full bg-secondary"
            style={{
              shadowColor: '#7527F5',
              shadowOpacity: 0.25,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <View className="h-[58px] w-[58px] items-center justify-center rounded-full bg-brand">
              <Icon name="crosshair" size={26} className="text-brand-foreground" />
            </View>
          </View>
          <Chapter
            eyebrow="Up next"
            title="Glucose test"
            body="Prepare for week 26"
            className="absolute right-[58px] top-[252px] w-[150px]"
            onPress={() => router.push('/visit-copilot')}
          />

          <View className="absolute left-[10px] top-[340px]">
            <Node label="" locked />
          </View>
          <Chapter
            eyebrow=""
            title="Sleep & comfort"
            body="Unlocks at week 26"
            className="absolute left-[80px] top-[350px] w-[145px]"
          />

          <Chapter
            eyebrow=""
            title="Third trimester preview"
            body="Unlocks at week 27"
            className="absolute left-[98px] top-[466px] w-[170px]"
          />
          <View className="absolute right-[2px] top-[448px]">
            <Node label="" locked />
          </View>

          <View className="absolute left-[136px] top-[572px]">
            <Node label="28" active />
          </View>
          <Chapter
            eyebrow="Next chapter"
            title="Third trimester"
            body="Starts at week 28"
            className="absolute left-[90px] top-[646px] w-[170px]"
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
