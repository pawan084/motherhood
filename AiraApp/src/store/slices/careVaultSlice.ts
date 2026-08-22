import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type ActionStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type ShareExpiry = '24h' | '7d' | '30d';

export type VaultDocument = {
  id: string;
  title: string;
  fileType: 'pdf' | 'jpg' | 'png' | 'other';
  sizeLabel: string;
  dateLabel: string;
  section: 'thisTrimester' | 'earlier';
  icon: string;
};

export type VaultShareLink = {
  id: string;
  documentId: string;
  recipient: string;
  expiresAt: string;
  createdAt: string;
  revoked: boolean;
};

type UploadDraft = {
  fileName: string;
  sizeLabel: string;
  progress: number;
  status: 'idle' | 'uploading' | 'scanning' | 'completed' | 'failed' | 'cancelled';
};

type ShareDraft = {
  documentId: string | null;
  recipient: string;
  expires: ShareExpiry;
};

type CareVaultState = {
  status: LoadStatus;
  documents: VaultDocument[];
  activeShares: VaultShareLink[];
  selectedDocumentId: string | null;
  upload: UploadDraft;
  shareDraft: ShareDraft;
  shareStatus: ActionStatus;
  error: string | null;
};

const initialState: CareVaultState = {
  status: 'idle',
  documents: [],
  activeShares: [],
  selectedDocumentId: null,
  upload: {
    fileName: '',
    sizeLabel: '',
    progress: 0,
    status: 'idle',
  },
  shareDraft: {
    documentId: null,
    recipient: '',
    expires: '24h',
  },
  shareStatus: 'idle',
  error: null,
};

const careVaultSlice = createSlice({
  name: 'careVault',
  initialState,
  reducers: {
    careVaultLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    careVaultLoaded(
      state,
      action: PayloadAction<{
        documents: VaultDocument[];
        activeShares: VaultShareLink[];
      }>,
    ) {
      state.status = 'succeeded';
      state.documents = action.payload.documents;
      state.activeShares = action.payload.activeShares;
      state.error = null;
    },
    careVaultLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setSelectedVaultDocument(state, action: PayloadAction<string | null>) {
      state.selectedDocumentId = action.payload;
      state.shareDraft.documentId = action.payload;
    },
    uploadStarted(state, action: PayloadAction<{ fileName: string; sizeLabel: string }>) {
      state.upload = {
        fileName: action.payload.fileName,
        sizeLabel: action.payload.sizeLabel,
        progress: 0,
        status: 'uploading',
      };
      state.error = null;
    },
    uploadProgressChanged(
      state,
      action: PayloadAction<{ progress: number; status?: UploadDraft['status'] }>,
    ) {
      state.upload.progress = action.payload.progress;
      state.upload.status = action.payload.status ?? state.upload.status;
    },
    uploadSucceeded(state, action: PayloadAction<VaultDocument>) {
      state.upload.progress = 1;
      state.upload.status = 'completed';
      state.documents.unshift(action.payload);
      state.error = null;
    },
    uploadFailed(state, action: PayloadAction<string>) {
      state.upload.status = 'failed';
      state.error = action.payload;
    },
    uploadCancelled(state) {
      state.upload.status = 'cancelled';
    },
    setVaultShareRecipient(state, action: PayloadAction<string>) {
      state.shareDraft.recipient = action.payload;
    },
    setVaultShareExpiry(state, action: PayloadAction<ShareExpiry>) {
      state.shareDraft.expires = action.payload;
    },
    vaultShareStarted(state) {
      state.shareStatus = 'loading';
      state.error = null;
    },
    vaultShareSucceeded(state, action: PayloadAction<VaultShareLink>) {
      state.shareStatus = 'succeeded';
      state.activeShares.push(action.payload);
      state.error = null;
    },
    vaultShareFailed(state, action: PayloadAction<string>) {
      state.shareStatus = 'failed';
      state.error = action.payload;
    },
    revokeVaultShare(state, action: PayloadAction<string>) {
      const share = state.activeShares.find((item) => item.id === action.payload);

      if (share) {
        share.revoked = true;
      }
    },
    resetCareVaultState() {
      return initialState;
    },
  },
});

export const {
  careVaultLoaded,
  careVaultLoadFailed,
  careVaultLoadStarted,
  resetCareVaultState,
  revokeVaultShare,
  setSelectedVaultDocument,
  setVaultShareExpiry,
  setVaultShareRecipient,
  uploadCancelled,
  uploadFailed,
  uploadProgressChanged,
  uploadStarted,
  uploadSucceeded,
  vaultShareFailed,
  vaultShareStarted,
  vaultShareSucceeded,
} = careVaultSlice.actions;

export default careVaultSlice.reducer;
