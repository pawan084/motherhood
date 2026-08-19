import { Modal, Pressable, Text, View } from 'react-native';
import { router } from 'expo-router';

import { Icon, type IconName } from '@/components/icon';

const TOOLS: { title: string; body: string; icon: IconName }[] = [
  { title: 'Check in', body: 'Mood, energy & sleep', icon: 'heart' },
  { title: 'Reminder', body: 'Medicine or care task', icon: 'bell' },
  { title: 'Care Vault', body: 'Prescription or report', icon: 'briefcase' },
  { title: 'Reset', body: 'Two calm minutes', icon: 'wind' },
  { title: 'Track', body: 'Log a change', icon: 'activity' },
  { title: 'Companion', body: 'Voice or avatar mode', icon: 'mic' },
  { title: 'Calm match', body: 'A quiet minute of play', icon: 'zap' },
];

export function ChatToolsSheet({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View className="flex-1 justify-end">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close tools"
          onPress={onClose}
          className="absolute inset-0">
          <View className="flex-1 bg-foreground opacity-45" />
        </Pressable>

        <View className="rounded-t-[24px] bg-background px-5 pb-5 pt-5">
          <View className="flex-row items-start justify-between gap-4">
            <View className="flex-1">
              <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
                Tools in this conversation
              </Text>
              <Text className="mt-1 font-serif text-[26px] leading-8 text-foreground">
                What would help now?
              </Text>
            </View>
            <Pressable
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="Close tools"
              className="active:opacity-70">
              <View className="h-12 w-12 items-center justify-center rounded-full border border-border bg-card">
                <Icon name="x" size={22} className="text-muted-foreground" />
              </View>
            </Pressable>
          </View>

          <View className="mt-5 flex-row flex-wrap gap-3">
            {TOOLS.map((tool) => (
              <Pressable
                key={tool.title}
                onPress={() => {
                  if (tool.title === 'Care Vault') {
                    onClose();
                    router.push('/care-vault');
                  }
                  if (tool.title === 'Reminder') {
                    onClose();
                    router.push('/pause-reminders');
                  }
                }}
                accessibilityRole="button"
                className="w-[47%] active:opacity-80">
                <View className="min-h-[116px] rounded-[16px] border border-border bg-card p-4">
                  <View className="h-9 w-9 items-center justify-center rounded-full bg-secondary">
                    <Icon name={tool.icon} size={21} className="text-brand" />
                  </View>
                  <Text className="mt-5 font-rubik-semibold text-base text-foreground">
                    {tool.title}
                  </Text>
                  <Text className="mt-1 font-rubik text-xs text-muted-foreground">
                    {tool.body}
                  </Text>
                </View>
              </Pressable>
            ))}
          </View>

          <Pressable onPress={onClose} accessibilityRole="button" className="mt-4 active:opacity-80">
            <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
              <Text className="font-rubik-semibold text-base text-brand">Close tools</Text>
            </View>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}
